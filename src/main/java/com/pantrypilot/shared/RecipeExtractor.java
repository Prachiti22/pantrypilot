package com.pantrypilot.shared;

/** Provided by the AI module (Rohit). Used by the insights module for "what can I cook now". */
public interface RecipeExtractor {

    /**
     * Downloads a recipe page or YouTube video description and pulls out the ingredient list.
     *
     * @throws RecipeExtractionException when the link cannot be read or holds no recipe
     */
    Recipe extract(String url);
}
