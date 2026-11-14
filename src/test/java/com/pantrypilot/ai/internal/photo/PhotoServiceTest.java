package com.pantrypilot.ai.internal.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.ProposalBuilder;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.ai.internal.llm.LlmException;
import com.pantrypilot.ai.internal.llm.LlmRequest;
import com.pantrypilot.ai.testsupport.ScriptedLlm;
import com.pantrypilot.ai.testsupport.TestImages;
import com.pantrypilot.core.stub.StubCatalog;
import com.pantrypilot.shared.Direction;
import com.pantrypilot.shared.ProposedLine;
import com.pantrypilot.shared.Source;
import com.pantrypilot.shared.Unit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhotoServiceTest {

    private final ScriptedLlm llm = new ScriptedLlm();
    private final StubCatalog catalog = new StubCatalog();
    private final PhotoService service = new PhotoService(llm, catalog, new ProposalBuilder(catalog));
    private final byte[] photo = TestImages.blankPng();

    private ProposedLine line(ProposalResult result, String rawText) {
        return result.proposal().lines().stream()
                .filter(l -> l.rawText().equals(rawText)).findFirst().orElseThrow();
    }

    @Test
    void onePhotoBecomesAnInProposal() {
        llm.answer("""
                {"items":[
                  {"name":"Tomato","count":4,"estimatedWeightGrams":480,"confidence":0.9},
                  {"name":"rice","count":null,"estimatedWeightGrams":1000,"confidence":0.7},
                  {"name":"dragon fruit","count":1,"estimatedWeightGrams":null,"confidence":0.8},
                  {"name":"mystery jar","count":null,"estimatedWeightGrams":null,"confidence":0.9}]}""");

        ProposalResult result = service.process(List.of(photo), 3, 4);

        assertThat(result.proposal().source()).isEqualTo(Source.PHOTO);
        assertThat(result.proposal().direction()).isEqualTo(Direction.IN);
        assertThat(llm.requests().get(0).kind()).isEqualTo(LlmRequest.Kind.VISION);
        assertThat(llm.requests().get(0).images()).hasSize(1);

        ProposedLine tomato = line(result, "tomato");
        assertThat(tomato.quantity()).isEqualByComparingTo("4");
        assertThat(tomato.unit()).isEqualTo(Unit.COUNT);
        assertThat(tomato.ingredientId()).isNotNull();
        assertThat(tomato.confidence()).isEqualTo(0.9);

        ProposedLine rice = line(result, "rice");
        assertThat(rice.quantity()).isEqualByComparingTo("1000");
        assertThat(rice.unit()).isEqualTo(Unit.G);

        assertThat(line(result, "dragon fruit").ingredientId()).isNull();
        assertThat(line(result, "dragon fruit").confidence()).isLessThanOrEqualTo(0.3);

        ProposedLine unknownAmount = line(result, "mystery jar");
        assertThat(unknownAmount.quantity()).isEqualByComparingTo("1");
        assertThat(unknownAmount.confidence()).isLessThanOrEqualTo(0.3);
    }

    @Test
    void sameIngredientIsAddedWithinAPhotoAndNotDoubleCountedAcrossPhotos() {
        List<List<PhotoService.Seen>> perPhoto = List.of(
                List.of(new PhotoService.Seen("red onion", bd("2"), bd("300"), 0.8),
                        new PhotoService.Seen("onions", bd("1"), bd("150"), 0.6),
                        new PhotoService.Seen("egg", bd("6"), null, 0.9)),
                List.of(new PhotoService.Seen("onion", bd("2"), bd("310"), 0.9),
                        new PhotoService.Seen("banana", bd("5"), null, 0.9)));

        var merged = service.merge(perPhoto);

        assertThat(merged).hasSize(3);
        var onion = merged.get(0);
        // 2 + 1 in the first photo, 2 in the second: the larger count wins
        assertThat(onion.quantity()).isEqualByComparingTo("3");
        assertThat(onion.note()).contains("Seen in 2 photos");
        assertThat(merged.get(1).name()).isEqualTo("egg");
        assertThat(merged.get(2).name()).isEqualTo("banana");
    }

    @Test
    void photosAreAnalysedInParallelAndAFailedOneIsSkippedWithAWarning() {
        AtomicInteger calls = new AtomicInteger();
        llm.answerWith(request -> {
            if (calls.getAndIncrement() == 1) {
                throw new LlmException("timeout");
            }
            return "{\"items\":[{\"name\":\"apple\",\"count\":3,\"estimatedWeightGrams\":null,\"confidence\":0.9}]}";
        });

        ProposalResult result = service.process(List.of(photo, photo, photo), 1, 1);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(result.warnings()).hasSize(1).first().asString().contains("skipped");
        assertThat(result.meta()).containsEntry("photos", 3).containsEntry("photosAnalysed", 2);
        assertThat(line(result, "apple").quantity()).isEqualByComparingTo("3");
    }

    @Test
    void outOfRangeNumbersFromTheModelAreIgnored() {
        llm.answer("""
                {"items":[{"name":"apple","count":-3,"estimatedWeightGrams":99999999,"confidence":2},
                          {"name":"","count":1,"estimatedWeightGrams":null,"confidence":0.9}]}""");

        ProposalResult result = service.process(List.of(photo), 1, 1);

        assertThat(result.proposal().lines()).hasSize(1);
        assertThat(line(result, "apple").quantity()).isEqualByComparingTo("1");
        assertThat(line(result, "apple").confidence()).isLessThanOrEqualTo(0.4);
    }

    @Test
    void failsClearlyWhenNoPhotoCanBeAnalysedOrTheInputIsWrong() {
        llm.unavailable();
        assertThatThrownBy(() -> service.process(List.of(photo), 1, 1))
                .isInstanceOf(AiProcessingException.class).hasMessageContaining("not available");
        assertThatThrownBy(() -> service.process(List.of(), 1, 1)).isInstanceOf(AiProcessingException.class);
        assertThatThrownBy(() -> service.process(Collections.nCopies(7, photo), 1, 1))
                .isInstanceOf(AiProcessingException.class);
        assertThatThrownBy(() -> service.process(List.of("nope".getBytes()), 1, 1))
                .isInstanceOf(AiProcessingException.class);
    }

    private static java.math.BigDecimal bd(String s) {
        return new java.math.BigDecimal(s);
    }
}
