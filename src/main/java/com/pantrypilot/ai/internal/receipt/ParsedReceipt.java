package com.pantrypilot.ai.internal.receipt;

import java.util.List;

/**
 * @param store        store name, or {@code null}
 * @param purchaseDate ISO date (yyyy-MM-dd), or {@code null}
 */
public record ParsedReceipt(String store, String purchaseDate, List<ReceiptLine> lines) {
}
