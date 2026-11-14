package com.pantrypilot.ai.internal.receipt;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.ai.internal.DetectedLine;
import com.pantrypilot.ai.internal.ProposalBuilder;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.ai.internal.llm.LlmException;
import com.pantrypilot.ai.internal.ocr.ImageInput;
import com.pantrypilot.ai.internal.ocr.OcrEngine;
import com.pantrypilot.ai.internal.ocr.OcrException;
import com.pantrypilot.ai.internal.ocr.ReceiptImagePreprocessor;
import com.pantrypilot.ai.internal.units.Measured;
import com.pantrypilot.ai.internal.units.UnitNormalizer;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.Source;
import com.pantrypilot.shared.Unit;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Receipt scanning: clean the image, OCR it, parse the lines, and propose
 * adding the items to the inventory with the real prices that were paid.
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
    private static final int MAX_OCR_CHARS_IN_RESULT = 4000;

    private final OcrEngine ocr;
    private final LlmReceiptParser llmParser;
    private final ProposalBuilder proposals;
    private final boolean binarize;

    public ReceiptService(
            OcrEngine ocr, LlmReceiptParser llmParser, ProposalBuilder proposals, AiProperties properties) {
        this.ocr = ocr;
        this.llmParser = llmParser;
        this.proposals = proposals;
        this.binarize = properties.ocr().binarize();
    }

    public ProposalResult process(byte[] imageBytes, long householdId, long userId) {
        BufferedImage image = ReceiptImagePreprocessor.prepare(ImageInput.decode(imageBytes), binarize);
        String text;
        try {
            text = ocr.read(image);
        } catch (OcrException e) {
            throw new AiProcessingException("Receipt scanning is not available on this server", e);
        }
        if (text.isBlank()) {
            throw new AiProcessingException("No text could be read from that photo");
        }
        return fromText(text, householdId, userId);
    }

    /** Parses already-recognised receipt text. Separate from {@link #process} so it can be tested without OCR. */
    public ProposalResult fromText(String ocrText, long householdId, long userId) {
        List<String> warnings = new ArrayList<>();
        ParsedReceipt receipt;
        String parser = "model";
        try {
            receipt = llmParser.parse(ocrText);
        } catch (LlmException e) {
            log.warn("Receipt model parsing failed, using the layout parser instead: {}", e.getMessage());
            receipt = HeuristicReceiptParser.parse(ocrText);
            parser = "layout";
            warnings.add("The language model was unavailable, so item names were not expanded. Please check each line.");
        }

        List<DetectedLine> detected = new ArrayList<>();
        for (ReceiptLine line : receipt.lines()) {
            Measured measured = UnitNormalizer.normalize(line.quantity(), line.unitWord())
                    .orElse(new Measured(line.quantity(), Unit.COUNT));
            if (measured.quantity().signum() <= 0) {
                continue;
            }
            detected.add(new DetectedLine(
                    line.rawText(),
                    line.name(),
                    measured.quantity(),
                    measured.unit(),
                    unitPrice(line.lineTotal(), measured.quantity()),
                    line.confidence(),
                    line.lineTotal() == null ? null : "Line total " + line.lineTotal().setScale(2, RoundingMode.HALF_UP)));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("parser", parser);
        if (receipt.store() != null) {
            meta.put("store", receipt.store());
        }
        if (receipt.purchaseDate() != null) {
            meta.put("purchaseDate", receipt.purchaseDate());
        }
        meta.put("ocrText", ocrText.length() > MAX_OCR_CHARS_IN_RESULT
                ? ocrText.substring(0, MAX_OCR_CHARS_IN_RESULT)
                : ocrText);
        return proposals.build(
                householdId, userId, Source.RECEIPT, Direction.IN, null, null, detected, warnings, meta);
    }

    /** Price of one base unit, computed here rather than trusted from the model's arithmetic. */
    static BigDecimal unitPrice(BigDecimal lineTotal, BigDecimal quantity) {
        if (lineTotal == null || quantity.signum() <= 0) {
            return null;
        }
        return lineTotal.divide(quantity, 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
