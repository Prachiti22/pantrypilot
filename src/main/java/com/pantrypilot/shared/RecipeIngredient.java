package com.pantrypilot.shared;

import java.math.BigDecimal;

/**
 * One ingredient of an extracted recipe, for the recipe's own number of servings.
 *
 * @param name     the ingredient without amount or preparation ("onion", not "1 large onion, diced")
 * @param quantity {@code null} when the recipe gives no amount ("salt to taste")
 * @param unit     {@code null} when {@code quantity} is null
 */
public record RecipeIngredient(String rawText, String name, BigDecimal quantity, Unit unit) {
}
