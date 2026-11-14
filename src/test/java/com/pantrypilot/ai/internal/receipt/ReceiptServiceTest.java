package com.pantrypilot.ai.internal.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.ai.internal.ProposalBuilder;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.ai.internal.ocr.OcrEngine;
import com.pantrypilot.ai.testsupport.ScriptedLlm;
import com.pantrypilot.ai.testsupport.TestImages;
import com.pantrypilot.core.stub.StubCatalog;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.ProposedLine;
import com.pantrypilot.shared.Source;
import com.pantrypilot.shared.Unit;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptServiceTest {

    private static final String OCR_TEXT = """
            FRESH MART
            ORG BNS 2LB    3.49
            GV WHL MLK GAL 4.29
            BANANAS 3 @ 0.39  1.17
            PAPER TOWELS   5.99
            TOTAL         14.94
            """;

    private final ScriptedLlm llm = new ScriptedLlm();
    private String ocrText = OCR_TEXT;

    private ReceiptService service() {
        OcrEngine ocr = new OcrEngine() {
            @Override
            public String read(BufferedImage image) {
                return ocrText;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        AiProperties properties =
                new AiProperties(null, new AiProperties.Ocr("", "", "eng", false), null, null, null);
        return new ReceiptService(ocr, new LlmReceiptParser(llm), new ProposalBuilder(new StubCatalog()), properties);
    }

    @Test
    void modelLinesBecomeAnInProposalWithRealUnitPrices() {
        llm.answer("""
                {"store":"Fresh Mart","purchaseDate":"2026-11-10","lines":[
                  {"rawText":"ORG BNS 2LB","name":"organic beans","quantity":2,"unit":"lb","lineTotal":3.49,"confidence":0.9},
                  {"rawText":"GV WHL MLK GAL","name":"whole milk","quantity":1,"unit":"gal","lineTotal":4.29,"confidence":0.95},
                  {"rawText":"BANANAS 3 @ 0.39","name":"bananas","quantity":3,"unit":"each","lineTotal":1.17,"confidence":0.9}]}""");

        ProposalResult result = service().process(TestImages.blankPng(), 5, 6);

        assertThat(result.proposal().source()).isEqualTo(Source.RECEIPT);
        assertThat(result.proposal().direction()).isEqualTo(Direction.IN);
        assertThat(result.proposal().householdId()).isEqualTo(5);
        assertThat(result.proposal().createdBy()).isEqualTo(6);
        assertThat(result.meta()).containsEntry("store", "Fresh Mart").containsEntry("purchaseDate", "2026-11-10")
                .containsEntry("parser", "model");

        List<ProposedLine> lines = result.proposal().lines();
        assertThat(lines).hasSize(3);

        ProposedLine beans = lines.get(0);
        assertThat(beans.unit()).isEqualTo(Unit.G);
        assertThat(beans.quantity()).isEqualByComparingTo("907.184");
        // 3.49 for 907.184 g
        assertThat(beans.unitPrice()).isEqualByComparingTo("0.003847");
        assertThat(result.details().get(0).canonicalName()).isEqualTo("beans");

        ProposedLine milk = lines.get(1);
        assertThat(milk.unit()).isEqualTo(Unit.ML);
        assertThat(milk.quantity()).isEqualByComparingTo("3785.41");

        ProposedLine bananas = lines.get(2);
        assertThat(bananas.unit()).isEqualTo(Unit.COUNT);
        assertThat(bananas.unitPrice()).isEqualByComparingTo("0.39");
        assertThat(bananas.confidence()).isEqualTo(0.9);
    }

    @Test
    void aLineTheModelCannotPointToInTheOcrTextLosesConfidence() {
        llm.answer("""
                {"store":null,"purchaseDate":"last tuesday","lines":[
                  {"rawText":"AVOCADO HASS","name":"avocado","quantity":1,"unit":"each","lineTotal":1.99,"confidence":0.9}]}""");

        ProposalResult result = service().process(TestImages.blankPng(), 1, 1);

        assertThat(result.proposal().lines().get(0).confidence()).isEqualTo(0.45);
        assertThat(result.meta()).doesNotContainKey("purchaseDate").doesNotContainKey("store");
    }

    @Test
    void nonsenseFromTheModelIsDropped() {
        llm.answer("""
                {"store":"X","purchaseDate":null,"lines":[
                  {"rawText":"ORG BNS 2LB","name":"","quantity":1,"unit":"each","lineTotal":3.49,"confidence":0.9},
                  {"rawText":"ORG BNS 2LB","name":"beans","quantity":-2,"unit":"each","lineTotal":3.49,"confidence":0.9},
                  {"rawText":"ORG BNS 2LB","name":"beans","quantity":0,"unit":"each","lineTotal":3.49,"confidence":0.9},
                  {"rawText":"GV WHL MLK GAL","name":"milk","quantity":1,"unit":"gal","lineTotal":-4,"confidence":7}]}""");

        ProposalResult result = service().process(TestImages.blankPng(), 1, 1);

        assertThat(result.proposal().lines()).hasSize(1);
        ProposedLine milk = result.proposal().lines().get(0);
        assertThat(milk.unitPrice()).isNull();
        assertThat(milk.confidence()).isEqualTo(1.0);
    }

    @Test
    void whenTheModelIsDownTheLayoutParserIsUsedAndTheUserIsTold() {
        llm.unavailable();

        ProposalResult result = service().process(TestImages.blankPng(), 1, 1);

        assertThat(result.meta()).containsEntry("parser", "layout");
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.proposal().lines()).extracting(ProposedLine::rawText)
                .anyMatch(raw -> raw.startsWith("ORG BNS 2LB"))
                .noneMatch(raw -> raw.startsWith("TOTAL"));
        assertThat(result.proposal().lines()).allMatch(line -> line.confidence() <= 0.4);
    }

    @Test
    void aPhotoWithNoTextOrThatIsNotAnImageIsRefused() {
        ocrText = "  \n";
        assertThatThrownBy(() -> service().process(TestImages.blankPng(), 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("No text");
        assertThatThrownBy(() -> service().process("not an image".getBytes(), 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("not a JPEG or PNG");
    }
}
