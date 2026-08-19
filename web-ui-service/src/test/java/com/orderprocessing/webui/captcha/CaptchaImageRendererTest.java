package com.orderprocessing.webui.captcha;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaImageRendererTest {
    private final CaptchaImageRenderer renderer = new CaptchaImageRenderer();

    @BeforeAll
    static void renderWithoutADisplay() {
        // Containers have no display; Spring Boot sets the same flag on startup.
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void rendersADecodablePngAtTheRequestedSize() throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(renderer.renderPng("ABC234", 240, 72)));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(240);
        assertThat(image.getHeight()).isEqualTo(72);
    }

    @Test
    void producesADifferentRenderingEachTimeSoTheImageCannotBeMatchedByHash() {
        assertThat(renderer.renderPng("ABC234", 240, 72))
                .isNotEqualTo(renderer.renderPng("ABC234", 240, 72));
    }

    @Test
    void handlesTheShortestAndLongestAnswersTheServiceCanIssue() throws IOException {
        assertThat(ImageIO.read(new ByteArrayInputStream(renderer.renderPng("ABCD", 240, 72)))).isNotNull();
        assertThat(ImageIO.read(new ByteArrayInputStream(renderer.renderPng("ABCDEFGHJK", 240, 72)))).isNotNull();
    }
}
