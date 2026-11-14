package com.pantrypilot.ai.internal.ocr;

import com.recognition.software.jdeskew.ImageDeskew;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Cleans a receipt photo before OCR: grayscale, enlarge small images,
 * straighten a tilted page, and optionally threshold to black and white.
 */
public final class ReceiptImagePreprocessor {

    /** Tesseract works best when capital letters are around 30 pixels tall; narrow photos are doubled. */
    private static final int MIN_WIDTH = 1000;
    /** Photos larger than this are shrunk so OCR stays fast. */
    private static final int MAX_SIDE = 3000;
    private static final double MIN_SKEW_DEGREES = 0.5;
    private static final double MAX_SKEW_DEGREES = 20;

    private ReceiptImagePreprocessor() {
    }

    public static BufferedImage prepare(BufferedImage source, boolean binarize) {
        BufferedImage image = toGray(resize(source));
        image = deskew(image);
        return binarize ? otsuThreshold(image) : image;
    }

    static BufferedImage resize(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = 1;
        if (w < MIN_WIDTH) {
            scale = 2;
        }
        if (Math.max(w, h) * scale > MAX_SIDE) {
            scale = (double) MAX_SIDE / Math.max(w, h);
        }
        if (scale == 1) {
            return source;
        }
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static BufferedImage toGray(BufferedImage source) {
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    static BufferedImage deskew(BufferedImage gray) {
        double angle = new ImageDeskew(gray).getSkewAngle();
        if (Math.abs(angle) < MIN_SKEW_DEGREES || Math.abs(angle) > MAX_SKEW_DEGREES) {
            return gray;
        }
        return rotate(gray, -angle);
    }

    /**
     * Rotates around the centre onto a canvas large enough to hold the result.
     * The uncovered corners are filled with white: black corners look like ink
     * to Tesseract and make it lose the price column.
     */
    static BufferedImage rotate(BufferedImage gray, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int w = gray.getWidth();
        int h = gray.getHeight();
        int nw = (int) Math.ceil(w * cos + h * sin);
        int nh = (int) Math.ceil(h * cos + w * sin);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.translate((nw - w) / 2.0, (nh - h) / 2.0);
        g.rotate(radians, w / 2.0, h / 2.0);
        g.drawImage(gray, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Otsu's method: pick the threshold that best separates dark ink from light paper. */
    static BufferedImage otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int[] pixels = gray.getRaster().getSamples(0, 0, w, h, 0, new int[w * h]);
        int threshold = otsuLevel(pixels);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = pixels[i] > threshold ? 255 : 0;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        out.getRaster().setSamples(0, 0, w, h, 0, pixels);
        return out;
    }

    static int otsuLevel(int[] pixels) {
        int[] histogram = new int[256];
        for (int p : pixels) {
            histogram[p]++;
        }
        long total = pixels.length;
        long sumAll = 0;
        for (int i = 0; i < 256; i++) {
            sumAll += (long) i * histogram[i];
        }
        long sumBackground = 0;
        long weightBackground = 0;
        double best = -1;
        int level = 127;
        for (int t = 0; t < 256; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) {
                continue;
            }
            long weightForeground = total - weightBackground;
            if (weightForeground == 0) {
                break;
            }
            sumBackground += (long) t * histogram[t];
            double meanBackground = (double) sumBackground / weightBackground;
            double meanForeground = (double) (sumAll - sumBackground) / weightForeground;
            double diff = meanBackground - meanForeground;
            double between = (double) weightBackground * weightForeground * diff * diff;
            if (between > best) {
                best = between;
                level = t;
            }
        }
        return level;
    }
}
