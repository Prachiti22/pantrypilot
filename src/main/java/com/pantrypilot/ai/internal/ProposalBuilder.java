package com.pantrypilot.ai.internal;

import com.pantrypilot.shared.CatalogMatch;
import com.pantrypilot.shared.CatalogService;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.ProposedChange;
import com.pantrypilot.shared.ProposedLine;
import com.pantrypilot.shared.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Shared last step of every extractor: match each detected name to the catalog
 * and assemble the {@link ProposedChange}.
 */
@Component
public class ProposalBuilder {

    /** Lines the catalog cannot match are kept, but never shown as confident. */
    static final double UNMATCHED_CONFIDENCE_CAP = 0.3;

    private final CatalogService catalog;

    public ProposalBuilder(CatalogService catalog) {
        this.catalog = catalog;
    }

    public ProposalResult build(
            long householdId,
            long userId,
            Source source,
            Direction direction,
            String dishName,
            Integer servings,
            List<DetectedLine> detected,
            List<String> warnings,
            Map<String, Object> meta) {

        List<ProposedLine> lines = new ArrayList<>();
        List<LineDetail> details = new ArrayList<>();
        for (DetectedLine d : detected) {
            Optional<CatalogMatch> match = catalog.resolve(d.name());
            double model = clamp(d.modelConfidence());
            double confidence = match
                    .map(m -> Math.min(model, clamp(m.confidence())))
                    .orElse(Math.min(model, UNMATCHED_CONFIDENCE_CAP));
            lines.add(new ProposedLine(
                    d.rawText(),
                    match.map(CatalogMatch::ingredientId).orElse(null),
                    d.quantity(),
                    d.unit(),
                    d.unitPrice(),
                    round2(confidence)));
            details.add(new LineDetail(
                    d.name(),
                    match.map(CatalogMatch::canonicalName).orElse(null),
                    match.map(m -> round2(clamp(m.confidence()))).orElse(0.0),
                    d.note()));
        }
        ProposedChange change =
                new ProposedChange(householdId, userId, source, direction, dishName, servings, lines);
        return new ProposalResult(change, List.copyOf(details), List.copyOf(warnings), Map.copyOf(meta));
    }

    static double clamp(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
