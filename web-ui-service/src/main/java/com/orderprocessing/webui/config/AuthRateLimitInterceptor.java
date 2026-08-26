package com.orderprocessing.webui.config;

import com.orderprocessing.webui.exception.RateLimitedException;
import com.orderprocessing.webui.support.AttemptCounterStore;
import com.orderprocessing.webui.support.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Caps how often one address may reach the unauthenticated sign-in endpoints.
 *
 * <p>This is deliberately separate from the captcha thresholds. Those count only genuine credential failures, so
 * a degraded backend answering 503 can never push an innocent caller towards a challenge; the trade-off is that
 * such a caller could otherwise submit without limit. Counting every request here closes that gap without
 * distorting what the captcha reacts to.
 *
 * <p>The gateway already meters these routes per address at the edge. This duplicates that ceiling on purpose:
 * it lives beside the attempt counters that decide when a challenge appears, so the two stay tuned together,
 * and it still applies if this service is ever reached by anything other than the gateway.
 */
public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private static final String KEY_PREFIX = "order-platform:web-ui:rate:";
    static final String CAPTCHA_IMAGE_PATH = "/captcha/image";

    private final AttemptCounterStore counters;
    private final WebUiProperties.RateLimit settings;

    public AuthRateLimitInterceptor(AttemptCounterStore counters, WebUiProperties properties) {
        this.counters = counters;
        this.settings = properties.getRateLimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!settings.isEnabled()) return true;

        boolean challenge = CAPTCHA_IMAGE_PATH.equals(request.getRequestURI());
        // Only submissions are metered on the form routes: rendering or refreshing a page is not abuse, and
        // charging it would lock people out of the very page that explains why they were blocked.
        if (!challenge && !"POST".equalsIgnoreCase(request.getMethod())) return true;

        String bucket = challenge ? "challenge" : "submission";
        int allowance = challenge ? settings.getChallengesPerWindow() : settings.getSubmissionsPerWindow();
        long used = counters.increment(KEY_PREFIX + bucket + ":" + ClientAddress.of(request), settings.getWindow());
        if (used <= allowance) return true;

        if (challenge) {
            // The browser asks for this through an img tag, so an error page would only render as a broken image.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1L, settings.getWindow().toSeconds())));
            return false;
        }
        throw new RateLimitedException(settings.getWindow());
    }
}
