package com.pantrypilot.shared.events;

import java.time.Instant;

/** Published two days before and on the expiry day. Published by the core module (Prachiti), consumed by the insights module (Vishvak). */
public record ItemExpiringSoon(long householdId, long ingredientId, java.time.LocalDate expiresOn, Instant at) {
}
