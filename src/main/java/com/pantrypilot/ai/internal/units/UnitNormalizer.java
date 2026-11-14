package com.pantrypilot.ai.internal.units;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the unit words found on receipts and in recipes ("lb", "cups", "tbsp")
 * into the three base units. Weights become grams, volumes become millilitres,
 * and countable words ("cloves", "cans", "each") become a count.
 *
 * <p>This only converts within one dimension. Converting 2 tomatoes to grams
 * needs the ingredient's average weight and is the core module's job.
 */
public final class UnitNormalizer {

    private record Factor(Unit unit, BigDecimal factor) {
    }

    private static final Map<String, Factor> UNITS = new HashMap<>();

    static {
        mass("1", "g", "gram", "gm", "gr");
        mass("1000", "kg", "kilogram", "kilo");
        mass("0.001", "mg", "milligram");
        mass("28.3495", "oz", "ounce");
        mass("453.592", "lb", "pound");

        volume("1", "ml", "milliliter", "millilitre", "cc");
        volume("10", "cl", "centiliter", "centilitre");
        volume("100", "dl", "deciliter", "decilitre");
        volume("1000", "l", "liter", "litre", "lt");
        volume("4.92892", "tsp", "teaspoon");
        volume("14.7868", "tbsp", "tbs", "tbl", "tablespoon");
        volume("29.5735", "floz", "fluidounce");
        volume("236.588", "cup");
        volume("473.176", "pt", "pint");
        volume("946.353", "qt", "quart");
        volume("3785.41", "gal", "gallon");

        count("1", "", "each", "ea", "ct", "count", "pc", "pcs", "piece", "item", "unit", "whole",
                "clove", "can", "tin", "slice", "bunch", "sprig", "stalk", "head", "stick", "ear",
                "fillet", "filet", "package", "pkg", "pack", "pk", "packet", "jar", "bottle", "bag",
                "box", "carton", "loaf", "leaf", "rasher", "strip");
        count("12", "dozen", "doz", "dz");
    }

    private UnitNormalizer() {
    }

    /** True when the word is a unit this class knows, in singular or plural form. */
    public static boolean isKnown(String unitWord) {
        return lookup(unitWord) != null;
    }

    /**
     * Converts {@code quantity} of {@code unitWord} to a base unit.
     *
     * @return empty when the unit word is not recognised
     */
    public static Optional<Measured> normalize(BigDecimal quantity, String unitWord) {
        Factor f = lookup(unitWord);
        if (f == null || quantity == null) {
            return Optional.empty();
        }
        BigDecimal value = quantity.multiply(f.factor()).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        return Optional.of(new Measured(value, f.unit()));
    }

    private static Factor lookup(String unitWord) {
        if (unitWord == null) {
            return UNITS.get("");
        }
        String key = unitWord.toLowerCase(Locale.ROOT).replaceAll("[.\\s]", "");
        Factor f = UNITS.get(key);
        if (f == null && key.endsWith("es")) {
            f = UNITS.get(key.substring(0, key.length() - 2));
        }
        if (f == null && key.endsWith("s")) {
            f = UNITS.get(key.substring(0, key.length() - 1));
        }
        if (f == null && key.endsWith("ves")) {
            f = UNITS.get(key.substring(0, key.length() - 3) + "f");
        }
        return f;
    }

    private static void mass(String factor, String... names) {
        register(Unit.G, factor, names);
    }

    private static void volume(String factor, String... names) {
        register(Unit.ML, factor, names);
    }

    private static void count(String factor, String... names) {
        register(Unit.COUNT, factor, names);
    }

    private static void register(Unit unit, String factor, String... names) {
        Factor f = new Factor(unit, new BigDecimal(factor));
        for (String n : names) {
            UNITS.put(n, f);
        }
    }
}
