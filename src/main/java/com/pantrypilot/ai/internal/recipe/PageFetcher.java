package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.shared.RecipeExtractionException;

/** Downloads a web page. An interface so tests can supply saved pages. */
public interface PageFetcher {

    /**
     * @param finalUrl the address after redirects
     */
    record Page(String finalUrl, String html) {
    }

    /**
     * @throws RecipeExtractionException when the link is refused or the page cannot be downloaded
     */
    Page fetch(String url);
}
