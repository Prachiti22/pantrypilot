package com.pantrypilot.shared;

import java.math.BigDecimal;
import java.util.Optional;

/** Provided by the core module (Prachiti). Used by the AI and insights modules. */
public interface QuantityService {

    /**
     * Converts between count, grams and millilitres using the ingredient's average weight and density.
     *
     * @return empty when the ingredient has no data for that conversion (for example millilitres of onion)
     */
    Optional<BigDecimal> convert(BigDecimal quantity, Unit from, Unit to, long ingredientId);
}
