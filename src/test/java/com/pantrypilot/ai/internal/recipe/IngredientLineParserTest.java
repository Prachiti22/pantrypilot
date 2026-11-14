package com.pantrypilot.ai.internal.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.pantrypilot.shared.RecipeIngredient;
import com.pantrypilot.shared.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IngredientLineParserTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "2 tomatoes                               | tomatoes        | 2      | COUNT",
        "200 g tomatoes                           | tomatoes        | 200    | G",
        "200g paneer, cubed                       | paneer          | 200    | G",
        "1 1/2 cups whole milk                    | whole milk      | 354.88 | ML",
        "1½ cups whole milk                       | whole milk      | 354.88 | ML",
        "½ tsp salt                               | salt            | 2.46   | ML",
        "1/4 cup olive oil                        | olive oil       | 59.15  | ML",
        "2 tbsp. soy sauce                        | soy sauce       | 29.57  | ML",
        "1 lb ground beef                         | ground beef     | 453.59 | G",
        "8 oz mushrooms, sliced                   | mushrooms       | 226.8  | G",
        "4 fl oz cream                            | cream           | 118.29 | ML",
        "3 cloves garlic, minced                  | garlic          | 3      | COUNT",
        "2 garlic cloves finely chopped           | garlic cloves   | 2      | COUNT",
        "2 medium onions finely chopped           | onions          | 2      | COUNT",
        "1 large onion, diced                     | onion           | 1      | COUNT",
        "2 (14 oz) cans diced tomatoes            | diced tomatoes  | 793.79 | G",
        "2 x 400g tins plum tomatoes              | plum tomatoes   | 800    | G",
        "2-3 carrots                              | carrots         | 2.5    | COUNT",
        "1 to 2 limes                             | limes           | 1.5    | COUNT",
        "1.5 kg potatoes (about 6)                | potatoes        | 1500   | G",
        "- 500 ml milk                            | milk            | 500    | ML",
        "one egg                                  | egg             | 1      | COUNT",
        "75g parmesan grated, plus extra to serve | parmesan        | 75     | G",
        "1 dozen eggs                             | eggs            | 12     | COUNT",
        "500 gms roughly slit tomatoes            | tomatoes        | 500    | G",
        "300 gms Chicken breast boneless          | chicken breast  | 300    | G",
        "2 tbsp malt vinegar / 1.5 tbsp White Vinegar | malt vinegar | 29.57 | ML",
        "1 tbsp butter or ghee                    | butter          | 14.79  | ML",
        "4 rashers smoked streaky bacon           | smoked streaky bacon | 4 | COUNT",
    })
    void parsesAmountUnitAndName(String line, String name, String quantity, Unit unit) {
        RecipeIngredient parsed = IngredientLineParser.parse(line);
        assertThat(parsed.name()).isEqualTo(name);
        assertThat(parsed.quantity()).isEqualByComparingTo(quantity);
        assertThat(parsed.unit()).isEqualTo(unit);
        assertThat(parsed.rawText()).isEqualTo(line);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "salt to taste          | salt",
        "a pinch of salt        | salt",
        "Fresh cilantro, for garnish | cilantro",
        "olive oil for frying   | olive oil",
    })
    void linesWithoutAnAmountHaveNoQuantity(String line, String name) {
        RecipeIngredient parsed = IngredientLineParser.parse(line);
        assertThat(parsed.name()).isEqualTo(name);
        assertThat(parsed.quantity()).isNull();
        assertThat(parsed.unit()).isNull();
    }

    @Test
    void emptyOrSymbolOnlyLinesAreIgnored() {
        assertThat(IngredientLineParser.parse(null)).isNull();
        assertThat(IngredientLineParser.parse("   ")).isNull();
        assertThat(IngredientLineParser.parse("---")).isNull();
    }

    @Test
    void aZeroDenominatorDoesNotCrash() {
        RecipeIngredient parsed = IngredientLineParser.parse("1/0 cup sugar");
        assertThat(parsed.name()).isEqualTo("sugar");
        assertThat(parsed.quantity()).isNull();
    }
}
