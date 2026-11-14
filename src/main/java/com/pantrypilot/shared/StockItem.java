package com.pantrypilot.shared;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One ingredient's current stock as seen by one user. */
public record StockItem(
        long ingredientId,
        String name,
        BigDecimal count,
        BigDecimal grams,
        BigDecimal millilitres,
        LocalDate expiresOn) {
}
