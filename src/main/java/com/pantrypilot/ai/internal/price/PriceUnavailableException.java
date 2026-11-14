package com.pantrypilot.ai.internal.price;

/** No price could be found or estimated. The user can still type one in. */
public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
