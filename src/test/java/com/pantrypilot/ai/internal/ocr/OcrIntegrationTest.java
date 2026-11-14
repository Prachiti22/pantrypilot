package com.pantrypilot.ai.internal.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.ai.testsupport.TestImages;
import java.awt.image.BufferedImage;
import java.util.List;
import com.recognition.software.jdeskew.ImageDeskew;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs the real Tesseract engine. Skipped on machines where Tesseract is not installed. */
class OcrIntegrationTest {

    private static final List<String> RECEIPT = List.of(
            "FRESH MART", "ORGANIC BEANS    3.49", "WHOLE MILK       4.29", "BANANAS          1.17",
            "TOTAL            8.95");

    private static Tess4jOcrEngine engine;

    @BeforeAll
    static void requireTesseract() {
        engine = new Tess4jOcrEngine(
                new AiProperties(null, new AiProperties.Ocr("", "", "eng", false), null, null, null));
        assumeTrue(engine.isAvailable(), "Tesseract is not installed on this machine");
    }

    @Test
    void readsAPrintedReceipt() {
        String text = engine.read(ReceiptImagePreprocessor.prepare(TestImages.receipt(RECEIPT), false));

        assertThat(text).contains("FRESH MART", "ORGANIC BEANS", "3.49", "WHOLE MILK", "4.29", "8.95");
    }

    @Test
    void keepsEachPriceOnTheSameLineAsItsItem() {
        // wide gaps between name and price, as on a real receipt
        List<String> wide = List.of(
                "TRADER FRESH MARKET", "", "ORG BNS 2LB            3.49", "GV WHL MLK GAL         4.29",
                "BANANAS 3 @ 0.39       1.17", "CHKN BRST 1.52 LB      9.10", "", "SUBTOTAL              18.05",
                "TOTAL                 18.05");

        String text = engine.read(ReceiptImagePreprocessor.prepare(TestImages.receipt(wide), false));

        assertThat(text.lines().map(String::strip))
                .contains("ORG BNS 2LB 3.49", "GV WHL MLK GAL 4.29", "CHKN BRST 1.52 LB 9.10", "TOTAL 18.05");
    }

    @Test
    void readsATiltedReceiptAfterDeskewing() {
        BufferedImage tilted = tilt(TestImages.receipt(RECEIPT), 6);

        String text = engine.read(ReceiptImagePreprocessor.prepare(tilted, false));

        assertThat(text).contains("ORGANIC BEANS", "3.49", "WHOLE MILK", "4.29", "8.95");
    }

    @Test
    void deskewingBringsATiltedReceiptBackToLevel() {
        BufferedImage tilted = ReceiptImagePreprocessor.toGray(tilt(TestImages.receipt(RECEIPT), -8));
        assertThat(new ImageDeskew(tilted).getSkewAngle()).isCloseTo(-8, within(1.0));

        BufferedImage straightened = ReceiptImagePreprocessor.deskew(tilted);

        assertThat(new ImageDeskew(straightened).getSkewAngle()).isCloseTo(0, within(1.0));
        // the corners added by the rotation are paper-white, not black
        assertThat(straightened.getRaster().getSample(1, 1, 0)).isEqualTo(255);
    }

    /** A receipt photographed at an angle on a white table. */
    private static BufferedImage tilt(BufferedImage image, double degrees) {
        return ReceiptImagePreprocessor.rotate(ReceiptImagePreprocessor.toGray(image), degrees);
    }

    @Test
    void readsAReceiptAfterThresholding() {
        String text = engine.read(ReceiptImagePreprocessor.prepare(TestImages.receipt(RECEIPT), true));

        assertThat(text).contains("ORGANIC BEANS", "3.49");
    }

    @Test
    void otsuPicksALevelBetweenInkAndPaper() {
        int[] pixels = new int[1000];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i < 200 ? 20 + (i % 10) : 230 + (i % 10);
        }
        assertThat(ReceiptImagePreprocessor.otsuLevel(pixels)).isBetween(29, 229);
    }
}
