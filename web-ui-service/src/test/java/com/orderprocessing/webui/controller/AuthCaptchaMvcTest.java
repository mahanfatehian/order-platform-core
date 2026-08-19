package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.model.UiAuthenticatedUser;
import com.orderprocessing.webui.service.LoginAttemptService;
import com.orderprocessing.webui.service.UiAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AuthCaptchaMvcTest {
    private static final String IP = "203.0.113.7";

    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient authenticatedClient;
    @MockBean PlatformClient platformClient;
    @MockBean UiAuthenticationService authenticationService;
    @MockBean LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        when(loginAttemptService.clientIp(any())).thenReturn(IP);
    }

    @Test
    void signInPageStaysFreeOfTheChallengeForOrdinaryVisitors() throws Exception {
        when(loginAttemptService.loginCaptchaRequired(IP)).thenReturn(false);

        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Security check"))))
                .andExpect(content().string(not(containsString("/captcha/image"))));
    }

    @Test
    void signInPageShowsTheChallengeOnceTheAddressCrossesTheThreshold() throws Exception {
        when(loginAttemptService.loginCaptchaRequired(IP)).thenReturn(true);

        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Security check")))
                .andExpect(content().string(containsString("/captcha/image")));
    }

    @Test
    void signInIsRefusedWithoutReachingTheBackendWhenTheChallengeIsUnanswered() throws Exception {
        when(loginAttemptService.loginCaptchaRequired(anyString(), eq(IP))).thenReturn(true);

        mvc.perform(post("/login").with(csrf())
                        .param("username", "johndoe").param("password", "Customer123!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the characters shown in the image")));

        verify(authenticationService, never()).authenticate(any(), any(), any());
        // An unanswered challenge is itself a failed attempt, otherwise the gate could be probed for free.
        verify(loginAttemptService).recordLoginFailure("johndoe", IP);
    }

    @Test
    void successfulSignInClearsTheCountersSoTheNextVisitIsUnchallenged() throws Exception {
        when(loginAttemptService.loginCaptchaRequired(anyString(), eq(IP))).thenReturn(false);
        when(authenticationService.authenticate(any(), any(), any()))
                .thenReturn(new UiAuthenticatedUser(UUID.randomUUID(), "johndoe", Set.of("USER")));

        mvc.perform(post("/login").with(csrf())
                        .param("username", "johndoe").param("password", "Customer123!"))
                .andExpect(status().is3xxRedirection());

        verify(loginAttemptService).clearLoginFailures("johndoe", IP);
    }

    @Test
    void challengeImageIsReachableAnonymouslyAndMustNeverBeCached() throws Exception {
        mvc.perform(get("/captcha/image")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void registrationPageShowsTheChallengeOnceSubmissionsPileUpFromOneAddress() throws Exception {
        when(loginAttemptService.registrationCaptchaRequired(IP)).thenReturn(true);

        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Security check")));
    }

    @Test
    void registrationIsRefusedWithoutReachingTheBackendWhenTheChallengeIsUnanswered() throws Exception {
        when(loginAttemptService.registrationCaptchaRequired(IP)).thenReturn(true);

        mvc.perform(post("/register").with(csrf())
                        .param("username", "newcomer").param("email", "newcomer@example.com")
                        .param("password", "Customer123!").param("confirmPassword", "Customer123!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the characters shown in the image")));

        verify(platformClient, never()).register(any());
    }
}
