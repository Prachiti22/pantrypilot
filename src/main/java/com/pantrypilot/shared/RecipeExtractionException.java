package com.pantrypilot.shared;

/** The link could not be fetched, or no recipe was found in it. The message is safe to show to the user. */
public class RecipeExtractionException extends RuntimeException {

    public RecipeExtractionException(String message) {
        super(message);
    }

    public RecipeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
