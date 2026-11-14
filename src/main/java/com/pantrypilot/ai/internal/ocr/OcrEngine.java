package com.pantrypilot.ai.internal.ocr;

import java.awt.image.BufferedImage;

/** Reads printed text out of an image. */
public interface OcrEngine {

    /**
     * @return the recognised text, possibly empty
     * @throws OcrException when the engine is missing or fails
     */
    String read(BufferedImage image);

    /** True when the native engine and its language data can be found. Never throws. */
    boolean isAvailable();
}
