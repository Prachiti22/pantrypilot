package com.pantrypilot.shared;

import java.math.BigDecimal;

/**
 * What a dish would cost to order out. Always an estimate unless the user edited it.
 *
 * @param pricePerServing menu price of one serving, before fees, tax and tip
 * @param total           {@code pricePerServing * servings}
 * @param basis           where the number came from: WEB_SEARCH, MODEL or USER_EDIT
 * @param sampleCount     how many prices the median was taken over (0 for MODEL and USER_EDIT)
 * @param estimated       false only when the user typed the price in
 */
public record PriceEstimate(
        String dish,
        String city,
        int servings,
        BigDecimal pricePerServing,
        BigDecimal total,
        String currency,
        Basis basis,
        int sampleCount,
        boolean estimated) {

    public enum Basis {
        WEB_SEARCH, MODEL, USER_EDIT
    }
}
