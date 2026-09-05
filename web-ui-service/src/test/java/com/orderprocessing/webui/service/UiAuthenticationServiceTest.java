package com.orderprocessing.webui.service;

import com.orderprocessing.webui.client.PlatformClient;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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
}
