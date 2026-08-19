package com.orderprocessing.webui.captcha;

import com.orderprocessing.webui.config.WebUiProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Issues and verifies captcha challenges. The expected answer never reaches the browser: it is held in the
 * server-side session and discarded the moment a verification is attempted.
 */
@Service
public class CaptchaService {
    /** Deliberately excludes 0/O and 1/I/L, which readers routinely confuse in a distorted image. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    public static final String SESSION_ATTRIBUTE = "com.orderprocessing.webui.CAPTCHA_CHALLENGE";

    private final CaptchaImageRenderer renderer;
    private final WebUiProperties.Captcha settings;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(CaptchaImageRenderer renderer, WebUiProperties properties) {
        this.renderer = renderer;
        this.settings = properties.getCaptcha();
    }

    /** Replaces any outstanding challenge and returns the PNG bytes for the new one. */
    public byte[] issue(HttpSession session) {
        String answer = randomAnswer();
        session.setAttribute(SESSION_ATTRIBUTE, new CaptchaChallenge(answer, Instant.now().plus(settings.getTtl())));
        return renderer.renderPng(answer, settings.getWidth(), settings.getHeight());
    }

    /**
     * Verifies a submitted answer. The challenge is consumed whether or not it matched, so a captcha can never be
     * replayed and a wrong guess always costs the caller a fresh image.
     */
    public boolean verify(HttpSession session, String submitted) {
        if (session == null) return false;
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return stored instanceof CaptchaChallenge challenge
                && !challenge.isExpired(Instant.now())
                && challenge.matches(submitted);
    }

    public void discard(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESSION_ATTRIBUTE);
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(settings.getLength());
        for (int i = 0; i < settings.getLength(); i++) {
            answer.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return answer.toString();
    }
}
