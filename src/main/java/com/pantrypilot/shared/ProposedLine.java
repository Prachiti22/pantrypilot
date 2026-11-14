package com.pantrypilot.shared;

import java.math.BigDecimal;

/**
 * One line of an AI proposal.
 *
 * @param rawText      the text or label the line came from, shown to the user on the review screen
 * @param ingredientId canonical ingredient id, or {@code null} when the catalog could not match the name
 * @param quantity     amount in {@code unit}; always positive, the direction is on the proposal
 * @param unit         COUNT, G or ML
 * @param unitPrice    price of ONE {@code unit} (one item, one gram or one millilitre), or {@code null} when unknown
 * @param confidence   0.0 to 1.0; low values are highlighted on the review screen
 */
public record ProposedLine(
        String rawText,
        Long ingredientId,
        BigDecimal quantity,
        Unit unit,
        BigDecimal unitPrice,
        double confidence) {
}
