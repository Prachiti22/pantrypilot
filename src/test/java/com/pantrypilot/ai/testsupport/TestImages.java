package com.pantrypilot.ai.testsupport;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;

/** Draws simple images for tests. */
public final class TestImages {

    private TestImages() {
    }

    /** Black monospaced text on white, one line per entry, like a printed receipt. */
    public static BufferedImage receipt(List<String> lines) {
        int lineHeight = 44;
        BufferedImage image = new BufferedImage(900, lineHeight * (lines.size() + 2), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        int y = lineHeight;
        for (String line : lines) {
            g.drawString(line, 30, y);
            y += lineHeight;
        }
        g.dispose();
        return image;
    }

    public static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] blankPng() {
        return png(receipt(List.of("x")));
    }
}
