package com.pantrypilot.shared;

import java.util.Optional;

/** Provided by the core module (Prachiti). Used by the AI and insights modules. */
public interface CatalogService {

    /**
     * Maps free text such as "roma tomato", "tomatoes" or "tamatar" to one canonical ingredient.
     *
     * @return empty when nothing in the catalog is close enough
     */
    Optional<CatalogMatch> resolve(String name);
}
