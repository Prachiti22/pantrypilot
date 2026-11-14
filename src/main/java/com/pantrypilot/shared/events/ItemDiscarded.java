package com.pantrypilot.shared.events;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;
import java.time.Instant;

/** An ingredient was thrown away. Published by the core module (Prachiti), consumed by the insights module (Vishvak). */
public record ItemDiscarded(long householdId, long userId, long ingredientId, BigDecimal quantity, Unit unit, String reason, Instant at) {
}
