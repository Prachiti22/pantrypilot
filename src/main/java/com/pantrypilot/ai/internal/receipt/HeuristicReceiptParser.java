package com.pantrypilot.ai.internal.receipt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads receipt lines without a language model, using only the layout
 * "ITEM NAME ..... 3.49". It cannot expand abbreviations, so it is the fallback
 * for when the model is unavailable and its lines get a low confidence.
 */
public final class HeuristicReceiptParser {

    static final double CONFIDENCE = 0.4;

    /** Text, then a price at the end of the line, optionally followed by a one or two letter tax flag. */
    private static final Pattern ITEM =
            Pattern.compile("^(.*?)[\\s.:]*\\$?\\s?(\\d{1,4}[.,]\\d{2})\\s*-?\\s*[A-Za-z]{0,2}$");
    /** "2 x", "2 @" or "2x" in front of the name. */
    private static final Pattern LEADING_QUANTITY = Pattern.compile("^(\\d{1,2})\\s*[xX@]\\s+(.*)$");
    /** "1.52 lb @ 1.99/lb" style weight lines. */
    private static final Pattern WEIGHT =
            Pattern.compile("(\\d+[.,]\\d+)\\s*(lb|lbs|kg|oz|g)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_AN_ITEM = Pattern.compile(
            "\\b(sub\\s*total|total|tax|cash|change|credit|debit|visa|mastercard|amex|balance|tender|"
                    + "payment|savings?|discount|coupon|you saved|loyalty|rewards?|points|auth|approval|"
                    + "card|items sold|refund|deposit|bag fee)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LETTERS = Pattern.compile("[A-Za-z]");

    private HeuristicReceiptParser() {
    }

    public static ParsedReceipt parse(String ocrText) {
        List<ReceiptLine> lines = new ArrayList<>();
        String store = null;
        for (String raw : ocrText.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (store == null && letterCount(line) >= 3 && !ITEM.matcher(line).matches()) {
                store = line;
            }
            Matcher item = ITEM.matcher(line);
            if (!item.matches() || NOT_AN_ITEM.matcher(line).find()) {
                continue;
            }
            String name = item.group(1).strip();
            BigDecimal total = new BigDecimal(item.group(2).replace(',', '.'));
            if (total.signum() <= 0) {
                continue;
            }

            BigDecimal quantity = BigDecimal.ONE;
            String unitWord = "each";
            Matcher weight = WEIGHT.matcher(name);
            Matcher leading = LEADING_QUANTITY.matcher(name);
            if (weight.find()) {
                quantity = new BigDecimal(weight.group(1).replace(',', '.'));
                unitWord = weight.group(2);
                name = name.substring(0, weight.start()).strip();
            } else if (leading.matches()) {
                quantity = new BigDecimal(leading.group(1));
                name = leading.group(2).strip();
            }
            name = cleanName(name);
            if (letterCount(name) < 2 || quantity.signum() <= 0) {
                continue;
            }
            lines.add(new ReceiptLine(line, name, quantity, unitWord, total, CONFIDENCE));
        }
        return new ParsedReceipt(store, null, lines);
    }

    /** Drops item codes and stray symbols around the name. */
    private static String cleanName(String name) {
        return name.replaceAll("\\b\\d{4,}\\b", " ")
                .replaceAll("[^A-Za-z0-9%&'/ -]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static int letterCount(String s) {
        int n = 0;
        Matcher m = LETTERS.matcher(s);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
