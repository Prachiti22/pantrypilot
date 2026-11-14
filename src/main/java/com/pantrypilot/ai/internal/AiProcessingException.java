package com.pantrypilot.ai.internal;

/** A photo, receipt or link could not be processed. The message is safe to show to the user. */
public class AiProcessingException extends RuntimeException {

    public AiProcessingException(String message) {
        super(message);
    }

    public AiProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
