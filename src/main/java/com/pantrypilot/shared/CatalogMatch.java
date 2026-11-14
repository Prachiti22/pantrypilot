package com.pantrypilot.shared;

/**
 * Result of matching free text against the canonical ingredient catalog.
 *
 * @param confidence 1.0 for an exact name or alias match, lower for fuzzy matches
 */
public record CatalogMatch(long ingredientId, String canonicalName, double confidence) {
}
