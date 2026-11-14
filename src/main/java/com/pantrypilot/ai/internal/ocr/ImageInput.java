package com.pantrypilot.ai.internal.ocr;

import com.pantrypilot.ai.internal.AiProcessingException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Decodes uploaded photos safely and shrinks them for the vision model. */
public final class ImageInput {

    /** Refuse images that would need a huge amount of memory once decoded. */
    private static final long MAX_PIXELS = 50_000_000L;

    private ImageInput() {
    }

    /**
     * @throws AiProcessingException when the bytes are not a JPEG or PNG, or the image is too large
     */
    public static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new AiProcessingException("That file is not a JPEG or PNG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new AiProcessingException("That image is too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new AiProcessingException("That image could not be read");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof AiProcessingException known) {
                throw known;
            }
            throw new AiProcessingException("That image could not be read", e);
        }
    }

    /** Re-encodes as JPEG with the longest side at most {@code maxSide} pixels. */
    public static byte[] toJpeg(BufferedImage source, int maxSide) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage rgb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, nw, nh, java.awt.Color.WHITE, null);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AiProcessingException("That image could not be converted", e);
        }
    }
}
