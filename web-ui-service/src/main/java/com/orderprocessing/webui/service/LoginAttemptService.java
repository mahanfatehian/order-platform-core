package com.orderprocessing.webui.service;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.support.AttemptCounterStore;
import com.orderprocessing.webui.support.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Counts unsuccessful sign-in activity so the sign-in form can escalate to a captcha only once a client starts
 * behaving like a script. Counters are shared through {@link AttemptCounterStore} rather than the session,
 * because a bot that discards its session cookie between attempts would reset a session-scoped counter for free.
 */
@Service
public class LoginAttemptService {
    private static final String KEY_PREFIX = "order-platform:web-ui:attempts:";
    /** Bounds the key size for hostile input; prefix collisions merely share a counter, which is harmless. */
    private static final int MAX_USERNAME_KEY_LENGTH = 64;

    private final AttemptCounterStore counters;
    private final WebUiProperties.Captcha settings;

    public LoginAttemptService(AttemptCounterStore counters, WebUiProperties properties) {
        this.counters = counters;
        this.settings = properties.getCaptcha();
    }

    public String clientIp(HttpServletRequest request) {
        return ClientAddress.of(request);
    }

    /** Address-only check, used when rendering the form before any username is known. */
    public boolean loginCaptchaRequired(String clientIp) {
        if (!settings.isEnabled()) return false;
        return counters.count(loginIpKey(clientIp)) >= settings.getFailureThreshold();
    }

    /**
     * A captcha is demanded when either the address or the targeted account has crossed the threshold, so both a
     * single host spraying many accounts and many hosts targeting one account are covered.
     */
    public boolean loginCaptchaRequired(String username, String clientIp) {
        if (!settings.isEnabled()) return false;
        return loginCaptchaRequired(clientIp)
                || counters.count(loginUserKey(username)) >= settings.getFailureThreshold();
    }

    public void recordLoginFailure(String username, String clientIp) {
        if (!settings.isEnabled()) return;
        counters.increment(loginIpKey(clientIp), settings.getWindow());
        counters.increment(loginUserKey(username), settings.getWindow());
    }

    public void clearLoginFailures(String username, String clientIp) {
        if (!settings.isEnabled()) return;
        counters.clear(loginIpKey(clientIp));
        counters.clear(loginUserKey(username));
    }

    /**
     * Registration counts every submission rather than only failures: a script creating accounts succeeds each
     * time, so failure-only accounting would never notice it.
     */
    public boolean registrationCaptchaRequired(String clientIp) {
        if (!settings.isEnabled()) return false;
        return counters.count(registerIpKey(clientIp)) >= settings.getRegistrationThreshold();
    }

    public void recordRegistrationAttempt(String clientIp) {
        if (!settings.isEnabled()) return;
        counters.increment(registerIpKey(clientIp), settings.getWindow());
    }

    private String loginIpKey(String clientIp) { return KEY_PREFIX + "login:ip:" + clientIp; }

    private String registerIpKey(String clientIp) { return KEY_PREFIX + "register:ip:" + clientIp; }

    private String loginUserKey(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_USERNAME_KEY_LENGTH) {
            normalized = normalized.substring(0, MAX_USERNAME_KEY_LENGTH);
        }
        return KEY_PREFIX + "login:user:" + normalized;
    }
}
