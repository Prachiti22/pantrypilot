package com.pantrypilot.ai.internal.ocr;

/** Tesseract is not installed or failed on an image. */
public class OcrException extends RuntimeException {

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
