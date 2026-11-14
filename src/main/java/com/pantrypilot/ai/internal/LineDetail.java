package com.pantrypilot.ai.internal;

/**
 * Review-screen detail for the proposal line at the same index.
 *
 * @param canonicalName   catalog name, or {@code null} when the catalog had no match
 * @param matchConfidence catalog match confidence, 0 when unmatched
 */
public record LineDetail(String detectedName, String canonicalName, double matchConfidence, String note) {
}
