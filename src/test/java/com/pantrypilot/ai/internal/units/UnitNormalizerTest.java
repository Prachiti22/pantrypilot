package com.pantrypilot.ai.internal.units;

import static org.assertj.core.api.Assertions.assertThat;

import com.pantrypilot.shared.Unit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitNormalizerTest {

    @ParameterizedTest
    @CsvSource({
        "2, lb, 907.184, G",
        "1, LBS, 453.592, G",
        "8, oz, 226.796, G",
        "1.5, kg, 1500, G",
        "1, cup, 236.588, ML",
        "2, cups, 473.176, ML",
        "1, Tbsp., 14.787, ML",
        "1, gal, 3785.41, ML",
        "3, cloves, 3, COUNT",
        "1, dozen, 12, COUNT",
        "2, loaves, 2, COUNT",
        "4, each, 4, COUNT",
    })
    void convertsToBaseUnits(String quantity, String unitWord, String expected, Unit unit) {
        Measured measured = UnitNormalizer.normalize(new BigDecimal(quantity), unitWord).orElseThrow();
        assertThat(measured.quantity()).isEqualByComparingTo(expected);
        assertThat(measured.unit()).isEqualTo(unit);
    }

    @Test
    void missingUnitMeansCount() {
        assertThat(UnitNormalizer.normalize(BigDecimal.TEN, null).orElseThrow())
                .isEqualTo(new Measured(BigDecimal.TEN, Unit.COUNT));
    }

    @Test
    void unknownWordsAreNotUnits() {
        assertThat(UnitNormalizer.isKnown("onion")).isFalse();
        assertThat(UnitNormalizer.normalize(BigDecimal.ONE, "smidgen")).isEmpty();
    }
}
