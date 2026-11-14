package com.pantrypilot.ai.internal;

import com.pantrypilot.shared.ProposedChange;
import java.util.List;
import java.util.Map;

/**
 * What an extractor returns: the proposal itself plus what the review screen needs to explain it.
 *
 * @param details  one entry per proposal line, same order
 * @param warnings things the user should know, such as lines that were skipped
 * @param meta     source-specific extras (store name, recipe title, OCR text)
 */
public record ProposalResult(
        ProposedChange proposal,
        List<LineDetail> details,
        List<String> warnings,
        Map<String, Object> meta) {
}
