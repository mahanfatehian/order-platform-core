package com.orderprocessing.webui.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;

/**
 * Draws a captcha challenge into a PNG using Java2D only, so the deployment keeps working without an external
 * captcha vendor, API keys, or outbound network access.
 */
@Component
public class CaptchaImageRenderer {
    private static final Color[] INK = {
            new Color(0x1B, 0x39, 0x5E), new Color(0x24, 0x57, 0xC5), new Color(0x0C, 0x24, 0x37),
            new Color(0x14, 0x75, 0x54), new Color(0x08, 0x79, 0x8F), new Color(0x81, 0x50, 0x00)
    };
    private static final String[] FAMILIES = {Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};

    private final SecureRandom random = new SecureRandom();

    public byte[] renderPng(String text, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            paintBackground(graphics, width, height);
            paintSpeckles(graphics, width, height);
            paintText(graphics, text, width, height);
            paintStrikeThrough(graphics, width, height);
        } finally {
            graphics.dispose();
        }
        return toPng(image);
    }

    private void paintBackground(Graphics2D graphics, int width, int height) {
        graphics.setPaint(new GradientPaint(0, 0, new Color(0xF4, 0xF7, 0xFA), width, height, new Color(0xED, 0xF2, 0xF7)));
        graphics.fillRect(0, 0, width, height);
    }

    /** Low-contrast dots break up the uniform background without hurting human readability. */
    private void paintSpeckles(Graphics2D graphics, int width, int height) {
        for (int i = 0; i < (width * height) / 90; i++) {
            graphics.setColor(new Color(0xB0 + random.nextInt(0x30), 0xC0 + random.nextInt(0x30), 0xD0 + random.nextInt(0x2F)));
            graphics.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
        }
    }

    private void paintText(Graphics2D graphics, String text, int width, int height) {
        int characters = Math.max(text.length(), 1);
        int cell = width / (characters + 1);
        int baseline = (height / 2) + (cell / 3);
        for (int i = 0; i < text.length(); i++) {
            Font font = new Font(FAMILIES[random.nextInt(FAMILIES.length)], Font.BOLD, cell + random.nextInt(8) - 2);
            GlyphVector glyphs = font.createGlyphVector(graphics.getFontRenderContext(), String.valueOf(text.charAt(i)));
            AffineTransform transform = AffineTransform.getTranslateInstance(
                    (cell / 2.0) + (i * cell) + random.nextInt(6) - 3,
                    baseline + random.nextInt(10) - 5);
            transform.rotate((random.nextDouble() - 0.5) * 0.7);
            transform.shear((random.nextDouble() - 0.5) * 0.28, 0);
            graphics.setColor(INK[random.nextInt(INK.length)]);
            graphics.fill(transform.createTransformedShape(glyphs.getOutline()));
        }
    }

    /** A couple of wandering strokes across the glyphs to frustrate naive segment-and-OCR scripts. */
    private void paintStrikeThrough(Graphics2D graphics, int width, int height) {
        graphics.setStroke(new BasicStroke(1.7f));
        for (int i = 0; i < 3; i++) {
            graphics.setColor(new Color(INK[random.nextInt(INK.length)].getRGB() & 0x00FFFFFF | 0x66000000, true));
            int startY = random.nextInt(height);
            graphics.drawPolyline(
                    new int[]{0, width / 3, (width * 2) / 3, width},
                    new int[]{startY, random.nextInt(height), random.nextInt(height), random.nextInt(height)},
                    4);
        }
    }

    private byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException exception) {
            throw new UncheckedIOException("Captcha image could not be encoded", exception);
        }
        return out.toByteArray();
    }
}
