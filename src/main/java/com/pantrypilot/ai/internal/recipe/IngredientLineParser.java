package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.ai.internal.units.Measured;
import com.pantrypilot.ai.internal.units.UnitNormalizer;
import com.pantrypilot.shared.RecipeIngredient;
import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one recipe ingredient line such as "1 1/2 cups whole milk",
 * "2 (14 oz) cans diced tomatoes" or "3 cloves garlic, minced" into a name,
 * a quantity and a base unit. No language model is involved.
 */
public final class IngredientLineParser {

    private static final Map<Character, String> UNICODE_FRACTIONS = Map.ofEntries(
            Map.entry('½', "1/2"), Map.entry('⅓', "1/3"), Map.entry('⅔', "2/3"),
            Map.entry('¼', "1/4"), Map.entry('¾', "3/4"), Map.entry('⅕', "1/5"),
            Map.entry('⅖', "2/5"), Map.entry('⅗', "3/5"), Map.entry('⅘', "4/5"),
            Map.entry('⅙', "1/6"), Map.entry('⅚', "5/6"), Map.entry('⅛', "1/8"),
            Map.entry('⅜', "3/8"), Map.entry('⅝', "5/8"), Map.entry('⅞', "7/8"));

    private static final Map<String, String> NUMBER_WORDS = Map.ofEntries(
            Map.entry("a", "1"), Map.entry("an", "1"), Map.entry("one", "1"), Map.entry("two", "2"),
            Map.entry("three", "3"), Map.entry("four", "4"), Map.entry("five", "5"), Map.entry("six", "6"),
            Map.entry("seven", "7"), Map.entry("eight", "8"), Map.entry("nine", "9"), Map.entry("ten", "10"),
            Map.entry("half", "0.5"));

    /** Words in front of the name that describe size or preparation, not the ingredient. */
    private static final Set<String> DESCRIPTORS = Set.of(
            "large", "small", "medium", "big", "fresh", "freshly", "finely", "roughly", "coarsely", "thinly",
            "chopped", "diced", "minced", "sliced", "grated", "shredded", "peeled", "crushed", "ripe",
            "heaped", "heaping", "level", "packed", "of");

    private static final String NUMBER = "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)";
    private static final Pattern QUANTITY =
            Pattern.compile("^(" + NUMBER + ")(?:\\s*(?:-|–|—|to)\\s*(" + NUMBER + "))?\\s*(.*)$");
    /** "2 (14 oz) cans tomatoes": a count of packages, each with a size. */
    private static final Pattern PACKAGE = Pattern.compile(
            "^\\(\\s*(" + NUMBER + ")\\s*-?\\s*((?:fl\\.?\\s*oz|fluid\\s+ounces?|[A-Za-z]+)\\.?)\\s*\\)\\s*(.*)$");
    /** "2 x 400g tins tomatoes": a count of packages written with a multiplication sign. */
    private static final Pattern MULTIPLIED = Pattern.compile(
            "^[xX×]\\s*(" + NUMBER + ")\\s*((?:fl\\.?\\s*oz|fluid\\s+ounces?|[A-Za-z]+)\\.?)(?![A-Za-z])\\s*(.*)$");
    /** Words that start the preparation part of a line: "onions finely chopped", "parmesan grated". */
    private static final Set<String> PREPARATION = Set.of(
            "finely", "roughly", "coarsely", "thinly", "thickly", "freshly", "chopped", "diced", "minced", "sliced",
            "grated", "shredded", "peeled", "crushed", "trimmed", "picked", "deseeded", "seeded", "halved",
            "quartered", "cut", "torn", "beaten", "melted", "softened", "drained", "rinsed", "cubed", "plus",
            "divided", "juiced", "zested", "cooked", "removed", "washed", "optional", "slit", "boneless",
            "skinless", "deboned", "soaked");
    private static final Pattern FLUID_OUNCE =
            Pattern.compile("^(fl\\.?\\s*oz\\.?|fluid\\s+ounces?)(?![A-Za-z])\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_WORD = Pattern.compile("^([A-Za-z]+)\\.?(?![A-Za-z])\\s*(.*)$");
    private static final Pattern TRAILING_NOTE = Pattern.compile(
            "\\s+(to taste|for (garnish|serving|frying|greasing|dusting)|as needed|optional|or more)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    /** Amounts too vague to deduct: "a pinch of salt", "a handful of basil". */
    private static final Pattern VAGUE_AMOUNT = Pattern.compile(
            "^(pinch|pinches|dash|dashes|splash|drizzle|handful|handfuls|knob|sprinkle)(?![A-Za-z])\\s*(?:of\\s+)?(.*)$",
            Pattern.CASE_INSENSITIVE);

    private IngredientLineParser() {
    }

    /**
     * @return the parsed line; quantity and unit are {@code null} when the line gives no amount.
     *         Returns {@code null} when the line holds no ingredient name at all.
     */
    public static RecipeIngredient parse(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String raw = rawLine.replace(' ', ' ').strip();
        String s = normalize(raw);
        if (s.isEmpty()) {
            return null;
        }

        BigDecimal quantity = null;
        String rest = s;
        Matcher q = QUANTITY.matcher(s);
        if (q.matches()) {
            quantity = number(q.group(1));
            if (q.group(2) != null) {
                // "2-3 carrots": take the middle of the range
                quantity = quantity.add(number(q.group(2))).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
            }
            rest = q.group(3);
        } else {
            Matcher w = FIRST_WORD.matcher(s);
            if (w.matches() && NUMBER_WORDS.containsKey(w.group(1).toLowerCase(Locale.ROOT)) && !w.group(2).isBlank()) {
                quantity = new BigDecimal(NUMBER_WORDS.get(w.group(1).toLowerCase(Locale.ROOT)));
                rest = w.group(2);
            }
        }

        Matcher vague = VAGUE_AMOUNT.matcher(rest);
        if (vague.matches() && !vague.group(2).isBlank()) {
            quantity = null;
            rest = vague.group(2);
        }

        Unit unit = null;
        boolean packaged = false;
        if (quantity != null) {
            Measured measured = null;
            Matcher pack = PACKAGE.matcher(rest);
            Matcher multiplied = MULTIPLIED.matcher(rest);
            if (multiplied.matches() && isMeasure(multiplied.group(2))) {
                BigDecimal total = quantity.multiply(number(multiplied.group(1)));
                measured = UnitNormalizer.normalize(total, multiplied.group(2)).orElse(null);
                rest = dropLeadingCountWord(multiplied.group(3));
                packaged = true;
            } else if (pack.matches() && isMeasure(pack.group(2))) {
                // 2 (14 oz) cans -> 28 oz; the container word that follows is dropped
                BigDecimal total = quantity.multiply(number(pack.group(1)));
                measured = UnitNormalizer.normalize(total, pack.group(2)).orElse(null);
                rest = dropLeadingCountWord(pack.group(3));
                packaged = true;
            } else {
                Matcher fluid = FLUID_OUNCE.matcher(rest);
                Matcher word = FIRST_WORD.matcher(rest);
                if (fluid.matches()) {
                    measured = UnitNormalizer.normalize(quantity, "floz").orElse(null);
                    rest = fluid.group(2);
                } else if (word.matches() && UnitNormalizer.isKnown(word.group(1)) && !word.group(2).isBlank()) {
                    measured = UnitNormalizer.normalize(quantity, word.group(1)).orElse(null);
                    rest = word.group(2);
                }
            }
            if (measured == null) {
                measured = new Measured(quantity, Unit.COUNT);
            }
            quantity = tidy(measured.quantity());
            unit = measured.unit();
        }

        String name = cleanName(rest, packaged);
        if (name.isEmpty()) {
            return null;
        }
        if (quantity != null && quantity.signum() <= 0) {
            quantity = null;
            unit = null;
        }
        return new RecipeIngredient(raw, name, quantity, unit);
    }

    private static boolean isMeasure(String unitWord) {
        return UnitNormalizer.normalize(BigDecimal.ONE, unitWord)
                .map(m -> m.unit() != Unit.COUNT)
                .orElse(false);
    }

    private static String dropLeadingCountWord(String rest) {
        Matcher word = FIRST_WORD.matcher(rest);
        if (word.matches() && UnitNormalizer.isKnown(word.group(1)) && !word.group(2).isBlank()) {
            return word.group(2);
        }
        return rest;
    }

    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            String fraction = UNICODE_FRACTIONS.get(c);
            if (fraction != null) {
                // "1½" must become "1 1/2", not "11/2"
                sb.append(' ').append(fraction).append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
                .replace('⁄', '/')
                .replaceAll("^[\\s\\-•*▢□·]+", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * @param packaged the line named a package size ("2 (14 oz) cans diced tomatoes"). The words in front
     *                 of the name then describe the product, so "diced tomatoes" is kept as it is, whereas
     *                 in "2 diced tomatoes" the word only says how to prepare them.
     */
    private static String cleanName(String rest, boolean packaged) {
        String name = rest.replaceAll("\\([^)]*\\)", " ");
        int comma = name.indexOf(',');
        if (comma >= 0) {
            name = name.substring(0, comma);
        }
        // "malt vinegar / white vinegar" or "butter or ghee": keep the first choice
        name = name.split("\\s+/\\s+|\\s+or\\s+", 2)[0];
        name = TRAILING_NOTE.matcher(name).replaceFirst("");
        name = name.replaceAll("[^\\p{L}\\p{N}%&' -]", " ").replaceAll("\\s+", " ").strip();
        boolean dropped = !packaged;
        while (dropped) {
            dropped = false;
            int space = name.indexOf(' ');
            String first = space > 0 ? name.substring(0, space).toLowerCase(Locale.ROOT) : "";
            if (DESCRIPTORS.contains(first) || PREPARATION.contains(first)) {
                name = name.substring(space + 1).strip();
                dropped = true;
            }
        }
        String[] words = name.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder kept = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            if (PREPARATION.contains(words[i])) {
                break;
            }
            kept.append(' ').append(words[i]);
        }
        return kept.toString();
    }

    private static BigDecimal number(String text) {
        String t = text.strip().replace(',', '.');
        if (t.contains("/")) {
            String[] whole = t.split("\\s+");
            String[] fraction = whole[whole.length - 1].split("/");
            BigDecimal denominator = new BigDecimal(fraction[1]);
            if (denominator.signum() == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal value = new BigDecimal(fraction[0]).divide(denominator, 4, RoundingMode.HALF_UP);
            return whole.length > 1 ? value.add(new BigDecimal(whole[0])) : value;
        }
        return new BigDecimal(t);
    }

    static BigDecimal tidy(BigDecimal value) {
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return v.scale() < 0 ? v.setScale(0) : v;
    }
}
