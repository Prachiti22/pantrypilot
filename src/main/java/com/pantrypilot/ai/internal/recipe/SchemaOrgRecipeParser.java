package com.pantrypilot.ai.internal.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.shared.Recipe;
import com.pantrypilot.shared.RecipeIngredient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Reads the schema.org Recipe data that most recipe sites embed for search
 * engines as JSON-LD. When it is there, no language model is needed.
 */
public final class SchemaOrgRecipeParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FIRST_INTEGER = Pattern.compile("(\\d{1,3})");
    private static final int MAX_DEPTH = 8;
    private static final int MAX_INGREDIENTS = 100;

    private SchemaOrgRecipeParser() {
    }

    public static Optional<Recipe> parse(Document page, String url) {
        for (Element script : page.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = MAPPER.readTree(script.data());
            } catch (JsonProcessingException e) {
                continue; // sites often ship broken JSON-LD; try the next block
            }
            JsonNode recipeNode = findRecipe(root, 0);
            if (recipeNode == null) {
                continue;
            }
            JsonNode ingredientNodes = recipeNode.has("recipeIngredient")
                    ? recipeNode.get("recipeIngredient")
                    : recipeNode.path("ingredients");
            List<RecipeIngredient> ingredients = new ArrayList<>();
            if (ingredientNodes.isArray()) {
                for (JsonNode node : ingredientNodes) {
                    if (ingredients.size() >= MAX_INGREDIENTS) {
                        break;
                    }
                    RecipeIngredient parsed = IngredientLineParser.parse(unescape(node.asText("")));
                    if (parsed != null) {
                        ingredients.add(parsed);
                    }
                }
            }
            if (ingredients.isEmpty()) {
                continue;
            }
            String title = unescape(recipeNode.path("name").asText("")).strip();
            return Optional.of(new Recipe(
                    title.isEmpty() ? page.title() : title, url, servings(recipeNode.path("recipeYield")), ingredients));
        }
        return Optional.empty();
    }

    /** The Recipe object may be the root, inside an array, or inside an "@graph" list. */
    private static JsonNode findRecipe(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findRecipe(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        if (isRecipeType(node.path("@type"))) {
            return node;
        }
        JsonNode inGraph = findRecipe(node.get("@graph"), depth + 1);
        return inGraph != null ? inGraph : findRecipe(node.get("mainEntity"), depth + 1);
    }

    private static boolean isRecipeType(JsonNode type) {
        if (type.isTextual()) {
            return type.asText().equalsIgnoreCase("Recipe");
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.asText("").equalsIgnoreCase("Recipe")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** recipeYield comes as 4, "4", "4 servings", "Makes 12 muffins" or a list of those. */
    static Integer servings(JsonNode yield) {
        if (yield.isArray()) {
            for (JsonNode y : yield) {
                Integer s = servings(y);
                if (s != null) {
                    return s;
                }
            }
            return null;
        }
        if (yield.isNumber()) {
            return yield.asInt() > 0 ? yield.asInt() : null;
        }
        Matcher m = FIRST_INTEGER.matcher(yield.asText(""));
        if (m.find()) {
            int value = Integer.parseInt(m.group(1));
            return value > 0 ? value : null;
        }
        return null;
    }

    private static String unescape(String s) {
        return Parser.unescapeEntities(s, false);
    }
}
