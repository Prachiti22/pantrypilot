package com.pantrypilot.ai.internal.llm;

/** The language model could not be reached or returned something unusable. */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
