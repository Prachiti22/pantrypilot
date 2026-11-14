package com.pantrypilot.ai.internal.units;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;

/** A quantity expressed in one of the three base units. */
public record Measured(BigDecimal quantity, Unit unit) {
}
