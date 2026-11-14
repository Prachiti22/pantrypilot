package com.pantrypilot.shared.events;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;
import java.time.Instant;

/** An ingredient was used. Published by the core module (Prachiti), consumed by the insights module (Vishvak). */
public record ItemConsumed(long householdId, long userId, long ingredientId, BigDecimal quantity, Unit unit, Instant at) {
}
