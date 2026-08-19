package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.service.LoginAttemptService;
import com.orderprocessing.webui.service.UiAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ceilings are set far below production values here so the boundary is reachable in a few requests.
 * Redis is absent in tests, which also exercises the in-process fallback the counters degrade to.
 */
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
        "spring.data.redis.password=test-only",
        "app.rate-limit.submissions-per-window=3",
        "app.rate-limit.challenges-per-window=2",
        "app.rate-limit.window=1m"
})
@AutoConfigureMockMvc
class AuthRateLimitMvcTest {
    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient authenticatedClient;
    @MockBean PlatformClient platformClient;
    @MockBean UiAuthenticationService authenticationService;
    @MockBean LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        when(loginAttemptService.clientIp(any())).thenReturn("127.0.0.1");
        // Force the captcha gate on so a submission is answered without reaching the backend.
        when(loginAttemptService.loginCaptchaRequired(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void refusesFurtherSignInSubmissionsOnceTheAddressExceedsItsAllowance() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(post("/login").with(csrf())
                    .param("username", "johndoe").param("password", "wrong")).andExpect(status().isOk());
        }

        mvc.perform(post("/login").with(csrf())
                        .param("username", "johndoe").param("password", "wrong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().string(containsString("Slow down for a moment")));

        // The ceiling has to hold before any credential work happens, or it would not bound anything.
        verify(authenticationService, never()).authenticate(any(), any(), any());
    }

    @Test
    void refusesFurtherChallengeImagesOnceTheAddressExceedsItsAllowance() throws Exception {
        mvc.perform(get("/captcha/image")).andExpect(status().isOk());
        mvc.perform(get("/captcha/image")).andExpect(status().isOk());

        // An img tag cannot render an error page, so the refusal is a bare status with a Retry-After.
        mvc.perform(get("/captcha/image"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void neverChargesAPageRenderAgainstTheSubmissionAllowance() throws Exception {
        for (int render = 0; render < 8; render++) {
            mvc.perform(get("/login")).andExpect(status().isOk());
        }
    }
}
