package com.pantrypilot.ai.internal.receipt;

import java.math.BigDecimal;

/**
 * One purchased item read from a receipt, before unit conversion.
 *
 * @param unitWord  unit as printed or inferred ("lb", "oz", "each"); may be {@code null}
 * @param lineTotal what was paid for the whole line, or {@code null}
 */
public record ReceiptLine(
        String rawText,
        String name,
        BigDecimal quantity,
        String unitWord,
        BigDecimal lineTotal,
        double confidence) {
}
