package com.pantrypilot.shared;

/** Provided by the AI module (Rohit). Used by the insights module for the money-saved tracker. */
public interface PriceEstimator {

    /** Typical price of ordering {@code servings} of {@code dish} in {@code city}. */
    PriceEstimate estimate(String dish, int servings, String city);
}
