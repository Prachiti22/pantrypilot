package com.pantrypilot.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/** A dish was cooked and its ingredients deducted. Published by the core module (Prachiti), consumed by the insights module (Vishvak). */
public record DishCooked(long householdId, long userId, String dishName, int servings, BigDecimal ingredientCost, Instant at) {
}
