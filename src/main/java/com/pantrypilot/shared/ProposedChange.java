package com.pantrypilot.shared;

import java.util.List;

/**
 * The shape of every AI proposal. The AI module never writes to the inventory;
 * it hands one of these to {@link PendingChangeService} and the user confirms,
 * edits or rejects it.
 *
 * @param dishName only for OUT proposals that belong to a dish, otherwise {@code null}
 * @param servings servings cooked, otherwise {@code null}
 */
public record ProposedChange(
        long householdId,
        long createdBy,
        Source source,
        Direction direction,
        String dishName,
        Integer servings,
        List<ProposedLine> lines) {

    public ProposedChange {
        lines = List.copyOf(lines);
    }
}
