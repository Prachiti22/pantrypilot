package com.pantrypilot.ai.internal.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.pantrypilot.shared.Recipe;
import com.pantrypilot.shared.RecipeIngredient;
import com.pantrypilot.shared.Unit;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class SchemaOrgRecipeParserTest {

    private static Optional<Recipe> parse(String jsonLd) {
        String html = "<html><head><title>Page title</title><script type=\"application/ld+json\">"
                + jsonLd + "</script></head><body></body></html>";
        return SchemaOrgRecipeParser.parse(Jsoup.parse(html), "https://example.com/r");
    }

    @Test
    void readsARecipeAtTheRoot() {
        Recipe recipe = parse("""
                {"@context":"https://schema.org","@type":"Recipe","name":"Palak Paneer","recipeYield":"4 servings",
                 "recipeIngredient":["300 g spinach","200 g paneer, cubed","2 tomatoes","salt to taste"]}""")
                .orElseThrow();

        assertThat(recipe.title()).isEqualTo("Palak Paneer");
        assertThat(recipe.servings()).isEqualTo(4);
        assertThat(recipe.url()).isEqualTo("https://example.com/r");
        assertThat(recipe.ingredients()).extracting(RecipeIngredient::name)
                .containsExactly("spinach", "paneer", "tomatoes", "salt");
        assertThat(recipe.ingredients().get(0).unit()).isEqualTo(Unit.G);
        assertThat(recipe.ingredients().get(3).quantity()).isNull();
    }

    @Test
    void findsTheRecipeInsideAGraphWithATypeList() {
        Recipe recipe = parse("""
                {"@context":"https://schema.org","@graph":[
                  {"@type":"WebSite","name":"Some blog"},
                  {"@type":["Recipe","NewsArticle"],"name":"Dal","recipeYield":["6","6 bowls"],
                   "recipeIngredient":["1 cup red lentils","1 onion"]}]}""")
                .orElseThrow();

        assertThat(recipe.title()).isEqualTo("Dal");
        assertThat(recipe.servings()).isEqualTo(6);
        assertThat(recipe.ingredients()).hasSize(2);
    }

    @Test
    void findsTheRecipeInsideATopLevelArrayAndDecodesEntities() {
        Recipe recipe = parse("""
                [{"@type":"BreadcrumbList"},
                 {"@type":"Recipe","name":"Mac &amp; Cheese","recipeYield":2,
                  "recipeIngredient":["8 oz macaroni","1 &frac12; cups milk"]}]""")
                .orElseThrow();

        assertThat(recipe.title()).isEqualTo("Mac & Cheese");
        assertThat(recipe.servings()).isEqualTo(2);
        assertThat(recipe.ingredients().get(1).quantity()).isEqualByComparingTo("354.88");
    }

    @Test
    void skipsBrokenJsonAndPagesWithoutARecipe() {
        assertThat(parse("{ this is not json")).isEmpty();
        assertThat(parse("{\"@type\":\"Article\",\"name\":\"News\"}")).isEmpty();
        assertThat(parse("{\"@type\":\"Recipe\",\"name\":\"Empty\",\"recipeIngredient\":[]}")).isEmpty();
    }

    @Test
    void usesThePageTitleWhenTheRecipeHasNoName() {
        Recipe recipe = parse("{\"@type\":\"Recipe\",\"recipeIngredient\":[\"1 egg\"]}").orElseThrow();
        assertThat(recipe.title()).isEqualTo("Page title");
        assertThat(recipe.servings()).isNull();
    }
}
