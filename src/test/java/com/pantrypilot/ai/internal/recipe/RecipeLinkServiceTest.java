package com.pantrypilot.ai.internal.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.ProposalBuilder;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.ai.testsupport.ScriptedLlm;
import com.pantrypilot.core.stub.StubCatalog;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.ProposedLine;
import com.pantrypilot.shared.Recipe;
import com.pantrypilot.shared.RecipeExtractionException;
import com.pantrypilot.shared.RecipeIngredient;
import com.pantrypilot.shared.Source;
import com.pantrypilot.shared.Unit;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeLinkServiceTest {

    private static final String RECIPE_PAGE = """
            <html><head><title>Blog</title><script type="application/ld+json">
            {"@type":"Recipe","name":"Palak Paneer","recipeYield":"4",
             "recipeIngredient":["300 g spinach","200 g paneer","2 tomatoes","1 onion","60 ml cream","salt to taste",
                                 "1 tsp garam masala"]}
            </script></head><body>Story of my life...</body></html>""";

    private final Map<String, String> pages = new HashMap<>();
    private final Map<String, VideoInfoFetcher.VideoInfo> videos = new HashMap<>();
    private final ScriptedLlm llm = new ScriptedLlm();
    private int transcriptRequests;
    private RecipeLinkService service;

    @BeforeEach
    void setUp() {
        PageFetcher pageFetcher = url -> {
            String html = pages.get(url);
            if (html == null) {
                throw new RecipeExtractionException("That website could not be reached");
            }
            return new PageFetcher.Page(url, html);
        };
        VideoInfoFetcher videoFetcher = new VideoInfoFetcher() {
            @Override
            public VideoInfo fetch(String url, boolean withTranscript) {
                if (withTranscript) {
                    transcriptRequests++;
                }
                VideoInfo info = videos.get(url);
                return withTranscript ? info : new VideoInfo(info.title(), info.description(), "");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        service = new RecipeLinkService(
                pageFetcher, videoFetcher, new LlmRecipeReader(llm), new ProposalBuilder(new StubCatalog()));
        pages.put("https://example.com/palak", RECIPE_PAGE);
    }

    @Test
    void structuredDataNeedsNoModelAndScalesToTheServingsMade() {
        ProposalResult result = service.propose("https://example.com/palak", 2, 7, 9);

        assertThat(llm.requests()).isEmpty();
        assertThat(result.proposal().householdId()).isEqualTo(7);
        assertThat(result.proposal().createdBy()).isEqualTo(9);
        assertThat(result.proposal().source()).isEqualTo(Source.LINK);
        assertThat(result.proposal().direction()).isEqualTo(Direction.OUT);
        assertThat(result.proposal().dishName()).isEqualTo("Palak Paneer");
        assertThat(result.proposal().servings()).isEqualTo(2);

        List<ProposedLine> lines = result.proposal().lines();
        assertThat(lines).extracting(ProposedLine::rawText)
                .containsExactly("300 g spinach", "200 g paneer", "2 tomatoes", "1 onion", "60 ml cream",
                        "1 tsp garam masala");
        assertThat(lines.get(0).quantity()).isEqualByComparingTo("150");
        assertThat(lines.get(0).unit()).isEqualTo(Unit.G);
        assertThat(lines.get(2).quantity()).isEqualByComparingTo("1");
        assertThat(lines.get(2).unit()).isEqualTo(Unit.COUNT);
        assertThat(lines.get(3).quantity()).isEqualByComparingTo("0.5");
        assertThat(lines.get(4).quantity()).isEqualByComparingTo("30");
        assertThat(result.meta()).containsEntry("method", "STRUCTURED_DATA").containsEntry("scale", "0.5");
    }

    @Test
    void matchedLinesAreConfidentAndUnknownIngredientsAreFlagged() {
        ProposalResult result = service.propose("https://example.com/palak", null, 1, 1);

        ProposedLine spinach = result.proposal().lines().get(0);
        assertThat(spinach.ingredientId()).isNotNull();
        assertThat(spinach.confidence()).isEqualTo(0.9);
        assertThat(result.details().get(0).canonicalName()).isEqualTo("spinach");

        ProposedLine garamMasala = result.proposal().lines().get(5);
        assertThat(garamMasala.ingredientId()).isNull();
        assertThat(garamMasala.confidence()).isLessThanOrEqualTo(0.3);
        assertThat(result.details().get(5).canonicalName()).isNull();
    }

    @Test
    void withoutServingsMadeTheRecipeAmountsAreUsedAsWritten() {
        ProposalResult result = service.propose("https://example.com/palak", null, 1, 1);

        assertThat(result.proposal().servings()).isEqualTo(4);
        assertThat(result.proposal().lines().get(0).quantity()).isEqualByComparingTo("300");
    }

    @Test
    void ingredientsWithoutAnAmountAreReportedNotDeducted() {
        ProposalResult result = service.propose("https://example.com/palak", 4, 1, 1);

        assertThat(result.proposal().lines()).extracting(ProposedLine::rawText).doesNotContain("salt to taste");
        assertThat(result.warnings()).anyMatch(w -> w.contains("salt"));
    }

    @Test
    void aPageWithoutStructuredDataIsReadByTheModel() {
        pages.put("https://example.com/blog", "<html><head><title>Grandma's dal</title></head>"
                + "<body><p>Take a cup of lentils and an onion...</p></body></html>");
        llm.answer("""
                {"isRecipe":true,"title":"Grandma's Dal","servings":2,"ingredients":[
                  {"rawText":"a cup of lentils","name":"lentils","quantity":1,"unit":"cup"},
                  {"rawText":"an onion","name":"onion","quantity":1,"unit":"each"},
                  {"rawText":"salt","name":"salt","quantity":null,"unit":null}]}""");

        ProposalResult result = service.propose("https://example.com/blog", 4, 1, 1);

        assertThat(llm.requests()).hasSize(1);
        assertThat(llm.requests().get(0).prompt()).contains("cup of lentils").doesNotContain("<p>");
        assertThat(result.meta()).containsEntry("method", "MODEL");
        assertThat(result.proposal().dishName()).isEqualTo("Grandma's Dal");
        assertThat(result.proposal().lines()).hasSize(2);
        assertThat(result.proposal().lines().get(0).quantity()).isEqualByComparingTo("473.18");
        assertThat(result.proposal().lines().get(0).unit()).isEqualTo(Unit.ML);
        assertThat(result.proposal().lines().get(0).confidence()).isEqualTo(0.6);
        assertThat(result.warnings()).anyMatch(w -> w.contains("language model"));
    }

    @Test
    void failuresBecomeMessagesForTheUser() {
        pages.put("https://example.com/news", "<html><body><p>Election results</p></body></html>");
        llm.answer("{\"isRecipe\":false,\"title\":null,\"servings\":null,\"ingredients\":[]}");
        assertThatThrownBy(() -> service.propose("https://example.com/news", 2, 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("No recipe");

        llm.unavailable();
        assertThatThrownBy(() -> service.propose("https://example.com/news", 2, 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("language model is not available");

        assertThatThrownBy(() -> service.propose("https://example.com/missing", 2, 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("could not be reached");
        assertThatThrownBy(() -> service.propose("ftp://example.com/x", 2, 1, 1))
                .isInstanceOf(AiProcessingException.class);
        assertThatThrownBy(() -> service.propose("https://example.com/palak", 0, 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("Servings");
    }

    @Test
    void aYouTubeDescriptionWithAnIngredientListNeedsNoModel() {
        videos.put("https://www.youtube.com/watch?v=abc", new VideoInfoFetcher.VideoInfo("Butter Chicken", """
                Today we cook butter chicken!

                INGREDIENTS:
                - 500 g chicken breast
                - 2 tbsp butter
                - 1 cup cream
                - 3 cloves garlic
                salt to taste

                METHOD
                1 Marinate the chicken for 30 minutes
                https://example.com/merch
                """, ""));

        ProposalResult result = service.propose("https://www.youtube.com/watch?v=abc", null, 1, 1);

        assertThat(llm.requests()).isEmpty();
        assertThat(result.meta()).containsEntry("method", "DESCRIPTION_LIST");
        assertThat(result.proposal().dishName()).isEqualTo("Butter Chicken");
        assertThat(result.proposal().lines()).hasSize(4);
        assertThat(result.proposal().lines().get(0).confidence()).isEqualTo(0.8);
    }

    @Test
    void aVideoWithoutAListFallsBackToTheModelAndThenTheCaptions() {
        videos.put("https://youtu.be/xyz", new VideoInfoFetcher.VideoInfo(
                "Quick omelette", "Subscribe for more!", "crack two eggs and add a splash of milk"));
        llm.answerWith(request -> request.prompt().contains("Spoken transcript")
                ? """
                  {"isRecipe":true,"title":"Omelette","servings":1,"ingredients":[
                    {"rawText":"two eggs","name":"egg","quantity":2,"unit":"each"}]}"""
                : "{\"isRecipe\":false,\"title\":null,\"servings\":null,\"ingredients\":[]}");

        Recipe recipe = service.extract("https://youtu.be/xyz");

        assertThat(transcriptRequests).isEqualTo(1);
        assertThat(llm.requests()).hasSize(2);
        assertThat(recipe.title()).isEqualTo("Omelette");
        assertThat(recipe.ingredients()).extracting(RecipeIngredient::name).containsExactly("egg");
    }

    @Test
    void aVideoWithNothingToReadFailsClearly() {
        videos.put("https://youtu.be/none", new VideoInfoFetcher.VideoInfo("Vlog", "My day", ""));
        llm.answer("{\"isRecipe\":false,\"title\":null,\"servings\":null,\"ingredients\":[]}");

        assertThatThrownBy(() -> service.extract("https://youtu.be/none"))
                .isInstanceOf(RecipeExtractionException.class).hasMessageContaining("no captions");
    }

    @Test
    void onlyRealYouTubeHostsCountAsYouTube() {
        assertThat(RecipeLinkService.isYouTube(URI.create("https://www.youtube.com/watch?v=1"))).isTrue();
        assertThat(RecipeLinkService.isYouTube(URI.create("https://m.youtube.com/watch?v=1"))).isTrue();
        assertThat(RecipeLinkService.isYouTube(URI.create("https://youtu.be/1"))).isTrue();
        assertThat(RecipeLinkService.isYouTube(URI.create("https://notyoutube.com/watch?v=1"))).isFalse();
        assertThat(RecipeLinkService.isYouTube(URI.create("https://youtube.com.evil.example/watch"))).isFalse();
    }
}
