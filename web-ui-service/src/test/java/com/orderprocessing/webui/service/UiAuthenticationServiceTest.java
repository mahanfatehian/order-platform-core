package com.orderprocessing.webui.service;

import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.LoginTokens;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.model.UiSessionTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiAuthenticationServiceTest {
    private final SessionTokenService tokenService = new SessionTokenService();
    private final PlatformClient platformClient = mock(PlatformClient.class);
    private final UiAuthenticationService service = new UiAuthenticationService(
            platformClient, tokenService, token -> null, new WebUiProperties());

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedRevocationFailures")
    void preservesSessionTokensWhenExpectedRevocationFails(String description, RuntimeException failure) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        UiSessionTokens tokens = new UiSessionTokens("access-token", "refresh-token",
                Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-01-02T04:04:05Z"));
        tokenService.save(request, tokens);
        doThrow(failure)
                .when(platformClient).logout("access-token");

        Throwable thrown = catchThrowable(service::logoutCurrentSession);
        assertThat(thrown).isNotNull().isNotSameAs(failure);
        assertThat(thrown.getCause()).isSameAs(failure);

        assertThat(tokenService.current()).contains(tokens);
    }

    private static Stream<Arguments> expectedRevocationFailures() {
        return Stream.of(
                Arguments.of("backend forbidden", new BackendClientException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Token is not valid", Map.of())),
                Arguments.of("backend missing", new BackendClientException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Token is not found", Map.of())),
                Arguments.of("transport unavailable", new ResourceAccessException("auth service unavailable")));
    }

    @Test
    void clearsTheSessionWhenThePlatformAlreadyRefusesTheToken() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        UiSessionTokens tokens = new UiSessionTokens("access-token", "refresh-token",
                Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-01-02T04:04:05Z"));
        tokenService.save(request, tokens);
        doThrow(new BackendClientException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token is not valid", Map.of()))
                .when(platformClient).logout("access-token");

        // 401 means the platform already refuses this token: revoked from another device, or expired. Revocation
        // has nothing left to achieve, so refusing to sign out locally would strand the browser signed in.
        assertThat(catchThrowable(service::logoutCurrentSession)).isNull();
        assertThat(tokenService.current()).isEmpty();
    }

    @Test
    void refreshesOncePerSessionEvenWhenRequestsArriveTogether() throws Exception {
        // Spring Session returns a fresh HttpSessionWrapper from every getSession() call, so concurrent requests
        // for one session hold different objects. This mirrors that: one shared MockHttpSession, but each thread
        // reaches it through its own request, exactly as the filter arranges it.
        Map<String, Object> sessionState = new java.util.concurrent.ConcurrentHashMap<>();
        UiSessionTokens expiring = new UiSessionTokens("access-old", "refresh-old",
                Instant.now().plusSeconds(5), Instant.now().plusSeconds(3600));
        MockHttpServletRequest seed = new MockHttpServletRequest();
        seed.setSession(new SharedStateSession("session-42", sessionState));
        tokenService.save(seed, expiring);

        AtomicInteger backendCalls = new AtomicInteger();
        when(platformClient.refresh("refresh-old")).thenAnswer(invocation -> {
            backendCalls.incrementAndGet();
            Thread.sleep(120L);            // widen the window the race needs
            return new LoginTokens("access-new", "refresh-new");
        });

        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setSession(new SharedStateSession("session-42", sessionState));
                    RequestContextHolder.setRequestAttributes(
                            new ServletRequestAttributes(request, new MockHttpServletResponse()));
                    try {
                        start.await();
                        service.refreshCurrentSession();
                    } catch (RuntimeException | InterruptedException ignored) {
                        // a losing thread is what the assertion below is about
                    } finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // The refresh token is single use. More than one call means the losers presented a spent token, and the
        // production path turns that into a forced sign-out mid-session.
        assertThat(backendCalls.get()).isEqualTo(1);
    }

    /**
     * Models what Spring Session actually hands a request: a distinct wrapper object per call, over one shared
     * session identity and one shared attribute map. Locking the wrapper therefore guards nothing.
     */
    private static final class SharedStateSession extends MockHttpSession {
        private final String id;
        private final Map<String, Object> state;

        private SharedStateSession(String id, Map<String, Object> state) {
            this.id = id;
            this.state = state;
        }

        @Override public String getId() { return id; }
        @Override public Object getAttribute(String name) { return state.get(name); }
        @Override public void setAttribute(String name, Object value) { state.put(name, value); }
        @Override public void removeAttribute(String name) { state.remove(name); }
    }
}
