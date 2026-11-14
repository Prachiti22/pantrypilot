package com.pantrypilot.ai.internal.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeuristicReceiptParserTest {

    private static final String RECEIPT = """
            FRESH MART #204
            123 Main St, Brooklyn NY

            ORG BNS 2LB            3.49 F
            GV WHL MLK GAL         4.29
            2 x BANANAS            1.18
            CHKN BRST 1.52 lb      9.10
            004011 TOMATOES        $2.49
            SUBTOTAL              20.55
            TAX                    0.00
            TOTAL                 20.55
            VISA ************1234 20.55
            THANK YOU
            """;

    @Test
    void readsItemLinesAndSkipsTotalsAndPayments() {
        ParsedReceipt receipt = HeuristicReceiptParser.parse(RECEIPT);

        assertThat(receipt.store()).isEqualTo("FRESH MART #204");
        assertThat(receipt.lines()).extracting(ReceiptLine::name)
                .containsExactly("ORG BNS 2LB", "GV WHL MLK GAL", "BANANAS", "CHKN BRST", "TOMATOES");
        assertThat(receipt.lines()).allMatch(line -> line.confidence() == HeuristicReceiptParser.CONFIDENCE);
    }

    @Test
    void readsPricesCountsAndWeights() {
        ParsedReceipt receipt = HeuristicReceiptParser.parse(RECEIPT);

        ReceiptLine beans = receipt.lines().get(0);
        assertThat(beans.lineTotal()).isEqualByComparingTo("3.49");
        assertThat(beans.quantity()).isEqualByComparingTo("1");

        ReceiptLine bananas = receipt.lines().get(2);
        assertThat(bananas.quantity()).isEqualByComparingTo("2");
        assertThat(bananas.lineTotal()).isEqualByComparingTo("1.18");

        ReceiptLine chicken = receipt.lines().get(3);
        assertThat(chicken.quantity()).isEqualByComparingTo("1.52");
        assertThat(chicken.unitWord()).isEqualTo("lb");
    }

    @Test
    void textWithoutPricesGivesNoLines() {
        assertThat(HeuristicReceiptParser.parse("hello\nworld\n").lines()).isEmpty();
        assertThat(HeuristicReceiptParser.parse("").lines()).isEmpty();
    }
}
