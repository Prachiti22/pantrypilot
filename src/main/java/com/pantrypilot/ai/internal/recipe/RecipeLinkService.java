package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.DetectedLine;
import com.pantrypilot.ai.internal.ProposalBuilder;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.ai.internal.llm.LlmException;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.Recipe;
import com.pantrypilot.shared.RecipeExtractionException;
import com.pantrypilot.shared.RecipeExtractor;
import com.pantrypilot.shared.RecipeIngredient;
import com.pantrypilot.shared.Source;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recipe link extraction. Websites are read from their schema.org data when
 * they have it and by the text model otherwise; YouTube links are read from
 * the description, then the captions.
 */
@Service
public class RecipeLinkService implements RecipeExtractor {

    private static final Logger log = LoggerFactory.getLogger(RecipeLinkService.class);

    /** How a recipe was obtained, which decides how much its lines are trusted. */
    enum Method {
        STRUCTURED_DATA(0.9), DESCRIPTION_LIST(0.8), MODEL(0.6);

        final double confidence;

        Method(double confidence) {
            this.confidence = confidence;
        }
    }

    record Extraction(Recipe recipe, Method method) {
    }

    private static final int MAX_SERVINGS = 100;
    private static final int MIN_DESCRIPTION_INGREDIENTS = 3;
    private static final Pattern INGREDIENTS_HEADING =
            Pattern.compile("^\\W*ingredients?\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_HEADING = Pattern.compile(
            "^\\W*(method|instructions?|directions?|steps?|preparation|how to|notes?|follow|subscribe|music|"
                    + "chapters?|timestamps?|equipment)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    private final PageFetcher pages;
    private final VideoInfoFetcher videos;
    private final LlmRecipeReader llmReader;
    private final ProposalBuilder proposals;
    private final UrlGuard syntaxOnly = new UrlGuard(true);

    public RecipeLinkService(
            PageFetcher pages, VideoInfoFetcher videos, LlmRecipeReader llmReader, ProposalBuilder proposals) {
        this.pages = pages;
        this.videos = videos;
        this.llmReader = llmReader;
        this.proposals = proposals;
    }

    @Override
    public Recipe extract(String url) {
        return extractWithMethod(url).recipe();
    }

    /**
     * Builds the deduction for cooking the recipe behind {@code url}.
     *
     * @param servingsMade how many servings the user cooked; {@code null} means the recipe's own amount
     */
    public ProposalResult propose(String url, Integer servingsMade, long householdId, long userId) {
        if (servingsMade != null && (servingsMade < 1 || servingsMade > MAX_SERVINGS)) {
            throw new AiProcessingException("Servings must be between 1 and " + MAX_SERVINGS);
        }
        Extraction extraction;
        try {
            extraction = extractWithMethod(url);
        } catch (RecipeExtractionException e) {
            throw new AiProcessingException(e.getMessage(), e);
        }
        Recipe recipe = extraction.recipe();
        List<String> warnings = new ArrayList<>();

        BigDecimal factor = BigDecimal.ONE;
        Integer servings = servingsMade != null ? servingsMade : recipe.servings();
        if (servingsMade != null) {
            if (recipe.servings() != null) {
                factor = BigDecimal.valueOf(servingsMade)
                        .divide(BigDecimal.valueOf(recipe.servings()), 6, RoundingMode.HALF_UP);
            } else {
                warnings.add("The recipe does not say how many servings it makes, so amounts were not scaled.");
            }
        }

        List<DetectedLine> detected = new ArrayList<>();
        List<String> withoutAmount = new ArrayList<>();
        for (RecipeIngredient ingredient : recipe.ingredients()) {
            if (ingredient.quantity() == null) {
                withoutAmount.add(ingredient.name());
                continue;
            }
            BigDecimal scaled = IngredientLineParser.tidy(ingredient.quantity().multiply(factor));
            if (scaled.signum() <= 0) {
                withoutAmount.add(ingredient.name());
                continue;
            }
            detected.add(new DetectedLine(
                    ingredient.rawText(), ingredient.name(), scaled, ingredient.unit(), null,
                    extraction.method().confidence, null));
        }
        if (!withoutAmount.isEmpty()) {
            warnings.add("No amount given, not deducted: " + String.join(", ", withoutAmount) + ".");
        }
        if (extraction.method() == Method.MODEL) {
            warnings.add("This recipe was read by the language model. Please check the amounts.");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("url", recipe.url());
        meta.put("title", recipe.title());
        meta.put("method", extraction.method().name());
        if (recipe.servings() != null) {
            meta.put("recipeServings", recipe.servings());
        }
        meta.put("scale", factor.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        return proposals.build(
                householdId, userId, Source.LINK, Direction.OUT, recipe.title(), servings, detected, warnings, meta);
    }

    Extraction extractWithMethod(String url) {
        URI uri = syntaxOnly.check(url);
        try {
            return isYouTube(uri) ? fromVideo(uri.toString()) : fromWebPage(uri.toString());
        } catch (LlmException e) {
            log.warn("Recipe model extraction failed for a link: {}", e.getMessage());
            throw new RecipeExtractionException(
                    "This link has no structured recipe data and the language model is not available to read it", e);
        }
    }

    private Extraction fromWebPage(String url) {
        PageFetcher.Page page = pages.fetch(url);
        Document document = Jsoup.parse(page.html(), page.finalUrl());
        Optional<Recipe> structured = SchemaOrgRecipeParser.parse(document, page.finalUrl());
        if (structured.isPresent()) {
            return new Extraction(structured.get(), Method.STRUCTURED_DATA);
        }
        document.select("script, style, noscript, nav, footer, header, form, iframe").remove();
        String text = document.body() == null ? "" : document.body().text();
        if (text.isBlank()) {
            throw new RecipeExtractionException("That page has no readable text");
        }
        Recipe recipe = llmReader.read(text, document.title(), page.finalUrl())
                .orElseThrow(() -> new RecipeExtractionException("No recipe was found on that page"));
        return new Extraction(recipe, Method.MODEL);
    }

    private Extraction fromVideo(String url) {
        VideoInfoFetcher.VideoInfo info = videos.fetch(url, false);
        List<RecipeIngredient> listed = ingredientsListedIn(info.description());
        if (listed.size() >= MIN_DESCRIPTION_INGREDIENTS) {
            return new Extraction(new Recipe(info.title(), url, null, listed), Method.DESCRIPTION_LIST);
        }
        String text = "Title: " + info.title() + "\n\nDescription:\n" + info.description();
        Optional<Recipe> fromDescription = llmReader.read(text, info.title(), url);
        if (fromDescription.isPresent()) {
            return new Extraction(fromDescription.get(), Method.MODEL);
        }
        String transcript = videos.fetch(url, true).transcript();
        if (transcript.isBlank()) {
            throw new RecipeExtractionException(
                    "That video has no ingredient list in its description and no captions to read");
        }
        Recipe recipe = llmReader.read("Title: " + info.title() + "\n\nSpoken transcript:\n" + transcript, info.title(), url)
                .orElseThrow(() -> new RecipeExtractionException("No recipe was found in that video"));
        return new Extraction(recipe, Method.MODEL);
    }

    /** Reads the lines under an "Ingredients" heading in a video description, up to the next heading. */
    static List<RecipeIngredient> ingredientsListedIn(String description) {
        List<RecipeIngredient> found = new ArrayList<>();
        boolean inList = false;
        int blankRun = 0;
        for (String raw : description.split("\\R")) {
            String line = raw.strip();
            if (!inList) {
                inList = INGREDIENTS_HEADING.matcher(line).matches() && line.length() < 60;
                continue;
            }
            if (line.isEmpty()) {
                if (++blankRun >= 2 && !found.isEmpty()) {
                    break;
                }
                continue;
            }
            blankRun = 0;
            if (OTHER_HEADING.matcher(line).matches() || line.toLowerCase(Locale.ROOT).startsWith("http")) {
                break;
            }
            RecipeIngredient parsed = IngredientLineParser.parse(line);
            if (parsed != null && parsed.quantity() != null && line.length() < 120) {
                found.add(parsed);
            }
        }
        return found;
    }

    static boolean isYouTube(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
    }
}
