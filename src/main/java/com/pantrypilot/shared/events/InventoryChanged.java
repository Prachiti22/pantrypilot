package com.pantrypilot.shared.events;

import java.time.Instant;

/** Stock of an ingredient changed for any reason. Published by the core module (Prachiti), consumed by the insights module (Vishvak). */
public record InventoryChanged(long householdId, long ingredientId, Instant at) {
}
