package com.pantrypilot.ai.internal;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;

/**
 * One item found by an extractor, before it is matched to the catalog.
 *
 * @param name            ingredient name as the extractor understood it
 * @param unitPrice       price of one {@code unit}, or {@code null}
 * @param modelConfidence how sure the extractor is, 0.0 to 1.0
 * @param note            extra detail for the review screen, or {@code null}
 */
public record DetectedLine(
        String rawText,
        String name,
        BigDecimal quantity,
        Unit unit,
        BigDecimal unitPrice,
        double modelConfidence,
        String note) {
}
