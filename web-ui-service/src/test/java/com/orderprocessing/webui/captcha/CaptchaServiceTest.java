package com.orderprocessing.webui.captcha;

import com.orderprocessing.webui.config.WebUiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaServiceTest {
    private final WebUiProperties properties = new WebUiProperties();
    private final CaptchaService service = new CaptchaService(new CaptchaImageRenderer(), properties);

    private CaptchaChallenge issuedChallenge(MockHttpSession session) {
        service.issue(session);
        return (CaptchaChallenge) session.getAttribute(CaptchaService.SESSION_ATTRIBUTE);
    }

    @Test
    void keepsTheExpectedAnswerServerSideAndNeverInTheRenderedImage() {
        MockHttpSession session = new MockHttpSession();
        byte[] image = service.issue(session);
        CaptchaChallenge challenge = (CaptchaChallenge) session.getAttribute(CaptchaService.SESSION_ATTRIBUTE);

        assertThat(challenge).isNotNull();
        assertThat(challenge.answer()).hasSize(properties.getCaptcha().getLength());
        assertThat(new String(image, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(challenge.answer());
    }

    @Test
    void acceptsTheIssuedAnswerRegardlessOfCasingOrSurroundingWhitespace() {
        MockHttpSession session = new MockHttpSession();
        CaptchaChallenge challenge = issuedChallenge(session);

        assertThat(service.verify(session, "  " + challenge.answer().toLowerCase() + "  ")).isTrue();
    }

    @Test
    void consumesTheChallengeSoASolvedCaptchaCannotBeReplayed() {
        MockHttpSession session = new MockHttpSession();
        CaptchaChallenge challenge = issuedChallenge(session);

        assertThat(service.verify(session, challenge.answer())).isTrue();
        assertThat(service.verify(session, challenge.answer())).isFalse();
    }

    @Test
    void consumesTheChallengeEvenWhenTheAnswerIsWrongSoGuessesCostAFreshImage() {
        MockHttpSession session = new MockHttpSession();
        issuedChallenge(session);

        assertThat(service.verify(session, "WRONG1")).isFalse();
        assertThat(session.getAttribute(CaptchaService.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void rejectsAnExpiredChallenge() {
        MockHttpSession session = new MockHttpSession();
        CaptchaChallenge expired = new CaptchaChallenge("ABCDEF", Instant.now().minus(Duration.ofSeconds(1)));
        session.setAttribute(CaptchaService.SESSION_ATTRIBUTE, expired);

        assertThat(service.verify(session, "ABCDEF")).isFalse();
    }

    @Test
    void rejectsBlankAnswersAndCallersWithoutASession() {
        MockHttpSession session = new MockHttpSession();
        issuedChallenge(session);

        assertThat(service.verify(session, "   ")).isFalse();
        assertThat(service.verify(null, "ABCDEF")).isFalse();
    }

    @Test
    void issuingAgainReplacesAnyOutstandingChallenge() {
        MockHttpSession session = new MockHttpSession();
        CaptchaChallenge first = issuedChallenge(session);
        CaptchaChallenge second = issuedChallenge(session);

        // A fresh image must invalidate the previous one; a collision across 31^6 answers is not a realistic risk.
        assertThat(second.answer()).isNotEqualTo(first.answer());
        assertThat(service.verify(session, second.answer())).isTrue();
    }

    @Test
    void omitsGlyphsThatReadersRoutinelyConfuse() {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 200; i++) {
            assertThat(issuedChallenge(session).answer())
                    .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]+");
        }
    }
}
