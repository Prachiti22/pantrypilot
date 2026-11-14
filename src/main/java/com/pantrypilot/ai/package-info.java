/**
 * AI Perception module (Rohit Shidid).
 *
 * <p>Turns a receipt photo, a grocery photo or a recipe link into a
 * {@link com.pantrypilot.shared.ProposedChange}, and estimates what a dish costs
 * to order out. It never changes the inventory: its only outputs are proposals
 * handed to {@link com.pantrypilot.shared.PendingChangeService} and price estimates.
 *
 * <p>Everything under {@code internal} is private to this module. Other modules
 * use it only through {@link com.pantrypilot.shared.PriceEstimator} and
 * {@link com.pantrypilot.shared.RecipeExtractor}.
 */
package com.pantrypilot.ai;
