package com.pantrypilot.insights.stub;

import com.pantrypilot.shared.PriceEstimate;
import com.pantrypilot.shared.PriceEstimator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLACEHOLDER for Vishvak's money-saved tracker. It applies the proposal's
 * formula to one dish, with the ingredient cost typed in by hand, to show the
 * insights module using {@link PriceEstimator} from the AI module.
 */
@RestController
@RequestMapping("/api/stub/insights")
public class StubMoneySavedController {

    /** Proposal defaults: 15% delivery and service fees, 8.875% sales tax, 15% tip. */
    private static final BigDecimal MARKUP = new BigDecimal("1.38875");

    private final PriceEstimator prices;

    public StubMoneySavedController(PriceEstimator prices) {
        this.prices = prices;
    }

    @GetMapping("/money-saved")
    public Map<String, Object> moneySaved(
            @RequestParam String dish,
            @RequestParam(defaultValue = "1") int servings,
            @RequestParam(required = false) String city,
            @RequestParam BigDecimal ingredientCost) {
        PriceEstimate estimate = prices.estimate(dish, servings, city);
        BigDecimal orderingOut = estimate.total().multiply(MARKUP).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("estimate", estimate);
        answer.put("orderingOutWithFeesTaxAndTip", orderingOut);
        answer.put("ingredientCost", ingredientCost);
        answer.put("saved", orderingOut.subtract(ingredientCost).setScale(2, RoundingMode.HALF_UP));
        return answer;
    }
}
