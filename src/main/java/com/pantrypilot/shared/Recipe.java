package com.pantrypilot.shared;

import java.util.List;

/**
 * A recipe pulled from a link.
 *
 * @param servings how many servings the recipe makes, or {@code null} when the page does not say
 */
public record Recipe(String title, String url, Integer servings, List<RecipeIngredient> ingredients) {

    public Recipe {
        ingredients = List.copyOf(ingredients);
    }
}
