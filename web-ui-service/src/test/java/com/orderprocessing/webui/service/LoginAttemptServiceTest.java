package com.orderprocessing.webui.service;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.support.AttemptCounterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {
    private static final String IP = "203.0.113.7";
    private static final String IP_KEY = "order-platform:web-ui:attempts:login:ip:" + IP;
    private static final String USER_KEY = "order-platform:web-ui:attempts:login:user:johndoe";
    private static final String REGISTER_KEY = "order-platform:web-ui:attempts:register:ip:" + IP;

    @Mock private AttemptCounterStore counters;

    private final WebUiProperties properties = new WebUiProperties();
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(counters, properties);
    }

    @Test
    void staysOutOfTheWayBelowTheThreshold() {
        when(counters.count(IP_KEY)).thenReturn(2L);
        when(counters.count(USER_KEY)).thenReturn(2L);

        assertThat(service.loginCaptchaRequired("johndoe", IP)).isFalse();
    }

    @Test
    void demandsACaptchaOnceOneAddressHasFailedEnoughTimes() {
        when(counters.count(IP_KEY)).thenReturn(3L);

        assertThat(service.loginCaptchaRequired(IP)).isTrue();
        assertThat(service.loginCaptchaRequired("johndoe", IP)).isTrue();
    }

    @Test
    void demandsACaptchaForATargetedAccountEvenFromAnAddressWithNoHistory() {
        when(counters.count(IP_KEY)).thenReturn(0L);
        when(counters.count(USER_KEY)).thenReturn(4L);

        assertThat(service.loginCaptchaRequired("JohnDoe", IP)).isTrue();
        // The address alone is still clean, so an unrelated user behind the same NAT is not challenged.
        assertThat(service.loginCaptchaRequired(IP)).isFalse();
    }

    @Test
    void recordsAFailureAgainstBothTheAddressAndTheTargetedAccount() {
        service.recordLoginFailure("  JohnDoe  ", IP);

        verify(counters).increment(IP_KEY, properties.getCaptcha().getWindow());
        verify(counters).increment(USER_KEY, properties.getCaptcha().getWindow());
    }

    @Test
    void clearsBothCountersOnASuccessfulSignIn() {
        service.clearLoginFailures("johndoe", IP);

        verify(counters).clear(IP_KEY);
        verify(counters).clear(USER_KEY);
    }

    @Test
    void countsEveryRegistrationSubmissionBecauseAScriptedSignupNeverFails() {
        service.recordRegistrationAttempt(IP);

        verify(counters).increment(REGISTER_KEY, properties.getCaptcha().getWindow());
    }

    @Test
    void boundsTheAccountKeySoAHostileUsernameCannotBloatTheCounterStore() {
        service.recordLoginFailure("x".repeat(500), IP);

        verify(counters).increment("order-platform:web-ui:attempts:login:user:" + "x".repeat(64),
                properties.getCaptcha().getWindow());
    }

    @Test
    void touchesTheCounterStoreNotAtAllWhenTheFeatureIsSwitchedOff() {
        properties.getCaptcha().setEnabled(false);
        LoginAttemptService disabled = new LoginAttemptService(counters, properties);

        assertThat(disabled.loginCaptchaRequired("johndoe", IP)).isFalse();
        assertThat(disabled.registrationCaptchaRequired(IP)).isFalse();
        disabled.recordLoginFailure("johndoe", IP);
        disabled.recordRegistrationAttempt(IP);
        disabled.clearLoginFailures("johndoe", IP);

        verifyNoInteractions(counters);
    }

    @Test
    void neverBuildsAnAccountKeyOutOfTheLiteralNull() {
        service.recordLoginFailure(null, IP);

        verify(counters).increment("order-platform:web-ui:attempts:login:user:", properties.getCaptcha().getWindow());
        verify(counters, never()).increment(contains("null"), any());
    }
}
