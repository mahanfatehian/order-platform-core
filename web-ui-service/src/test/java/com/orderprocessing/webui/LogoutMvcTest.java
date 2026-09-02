package com.orderprocessing.webui;

import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.model.UiSessionTokens;
import com.orderprocessing.webui.service.SessionTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.session.store-type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "app.security.jwt-secret=test-only-jwt-secret-that-is-long-enough-012345678901234567890123456789",
        "app.services.auth-url=http://localhost:18081",
        "app.services.user-url=http://localhost:18082",
        "app.services.store-url=http://localhost:18083",
        "app.services.order-url=http://localhost:18084",
        "app.services.store-internal-api-key=test-only-store-internal-key-0123456789",
        "spring.data.redis.password=test-only"
})
@AutoConfigureMockMvc
class LogoutMvcTest {
    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokenService;
    @MockBean PlatformClient platformClient;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedRevocationFailures")
    void expectedRevocationFailureAlwaysRendersServiceUnavailableWithoutLocalCleanup(
            String description, RuntimeException failure) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("logout-sentinel", "present");
        UiSessionTokens tokens = new UiSessionTokens("access-token", "refresh-token",
                Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-01-02T04:04:05Z"));
        MockHttpServletRequest tokenRequest = new MockHttpServletRequest();
        tokenRequest.setSession(session);
        tokenService.save(tokenRequest, tokens);
        doThrow(failure).when(platformClient).logout(tokens.accessToken());

        mvc.perform(post("/logout").with(user("customer").roles("USER")).with(csrf()).session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(cookie().doesNotExist("ORDER_PLATFORM_SESSION"));

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute("logout-sentinel")).isEqualTo("present");
        MockHttpServletRequest checkRequest = new MockHttpServletRequest();
        checkRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(checkRequest, new MockHttpServletResponse()));
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
}
