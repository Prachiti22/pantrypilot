package com.pantrypilot.ai.internal.price;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebPriceSearchTest {

    @Test
    void pullsPlausibleDollarPricesOutOfSearchSnippets() throws Exception {
        String json = """
                {"results":[
                  {"title":"Palak Paneer - $17.99 - Saravana Bhavan menu","content":"Fresh spinach and paneer. $17.99"},
                  {"title":"Menu","content":"Palak paneer $16.50, naan $4, lunch special $14"},
                  {"title":"Catering","content":"Tray of palak paneer $1,250.00 serves 80. Tip jar $0.50. Party $350"},
                  {"title":"No prices here","content":"Open 7 days"}]}""";

        List<BigDecimal> prices = WebPriceSearch.pricesIn(new ObjectMapper().readTree(json));

        assertThat(prices).extracting(BigDecimal::toPlainString)
                .containsExactly("17.99", "17.99", "16.50", "4", "14");
    }

    @Test
    void anAnswerWithoutResultsGivesNoPrices() throws Exception {
        assertThat(WebPriceSearch.pricesIn(new ObjectMapper().readTree("{}"))).isEmpty();
    }

    @Test
    void medianOfOddAndEvenSamples() {
        assertThat(PriceEstimatorService.median(List.of(bd("18"), bd("12"), bd("40"))))
                .isEqualByComparingTo("18");
        assertThat(PriceEstimatorService.median(List.of(bd("18"), bd("12"), bd("40"), bd("15"))))
                .isEqualByComparingTo("16.50");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
