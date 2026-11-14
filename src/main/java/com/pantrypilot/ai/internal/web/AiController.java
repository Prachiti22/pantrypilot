package com.pantrypilot.ai.internal.web;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.ai.internal.jobs.AiJob;
import com.pantrypilot.ai.internal.jobs.AiJobService;
import com.pantrypilot.ai.internal.jobs.JobStore;
import com.pantrypilot.ai.internal.jobs.JobType;
import com.pantrypilot.ai.internal.llm.LlmClient;
import com.pantrypilot.ai.internal.ocr.ImageInput;
import com.pantrypilot.ai.internal.ocr.OcrEngine;
import com.pantrypilot.ai.internal.photo.PhotoService;
import com.pantrypilot.ai.internal.price.PriceEstimatorService;
import com.pantrypilot.ai.internal.price.WebPriceSearch;
import com.pantrypilot.ai.internal.receipt.ReceiptService;
import com.pantrypilot.ai.internal.recipe.RecipeLinkService;
import com.pantrypilot.ai.internal.recipe.UrlGuard;
import com.pantrypilot.ai.internal.recipe.VideoInfoFetcher;
import com.pantrypilot.shared.PriceEstimate;
import com.pantrypilot.shared.RecipeExtractionException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API of the AI module.
 *
 * <p>The three POST endpoints only start a background job and answer
 * {@code 202 Accepted} with its id; the client then polls {@code GET /api/ai/jobs/{id}}.
 *
 * <p>{@code householdId} and {@code userId} are plain parameters for now. They
 * will come from the logged-in user's JWT once the core module's security is in.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final AiJobService jobs;
    private final JobStore store;
    private final ReceiptService receipts;
    private final PhotoService photos;
    private final RecipeLinkService links;
    private final PriceEstimatorService prices;
    private final LlmClient llm;
    private final OcrEngine ocr;
    private final VideoInfoFetcher videos;
    private final WebPriceSearch webSearch;
    private final AiProperties properties;
    private final UrlGuard linkSyntax = new UrlGuard(true);

    public AiController(
            AiJobService jobs,
            JobStore store,
            ReceiptService receipts,
            PhotoService photos,
            RecipeLinkService links,
            PriceEstimatorService prices,
            LlmClient llm,
            OcrEngine ocr,
            VideoInfoFetcher videos,
            WebPriceSearch webSearch,
            AiProperties properties) {
        this.jobs = jobs;
        this.store = store;
        this.receipts = receipts;
        this.photos = photos;
        this.links = links;
        this.prices = prices;
        this.llm = llm;
        this.ocr = ocr;
        this.videos = videos;
        this.webSearch = webSearch;
        this.properties = properties;
    }

    /** Scan one receipt photo. */
    @PostMapping(path = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobView> scanReceipt(
            @RequestParam("image") MultipartFile image,
            @RequestParam @Positive long householdId,
            @RequestParam @Positive long userId) {
        byte[] bytes = readImage(image);
        AiJob job = jobs.submit(JobType.RECEIPT, householdId, userId, image.getOriginalFilename(),
                () -> receipts.process(bytes, householdId, userId));
        return accepted(job);
    }

    /** Recognise groceries in one to six photos. */
    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobView> recognisePhotos(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam @Positive long householdId,
            @RequestParam @Positive long userId) {
        if (images.isEmpty() || images.size() > PhotoService.MAX_PHOTOS) {
            throw new AiProcessingException("Send between 1 and " + PhotoService.MAX_PHOTOS + " photos");
        }
        List<byte[]> all = new ArrayList<>();
        for (MultipartFile image : images) {
            all.add(readImage(image));
        }
        AiJob job = jobs.submit(JobType.PHOTO, householdId, userId, images.size() + " photo(s)",
                () -> photos.process(all, householdId, userId));
        return accepted(job);
    }

    public record RecipeLinkRequest(
            @NotBlank @Size(max = 2000) String url,
            @Min(1) @Max(100) Integer servingsMade,
            @NotNull @Positive Long householdId,
            @NotNull @Positive Long userId) {
    }

    /** Propose the deduction for cooking the recipe behind a link. */
    @PostMapping("/recipe-links")
    public ResponseEntity<JobView> recipeLink(@Valid @RequestBody RecipeLinkRequest request) {
        try {
            linkSyntax.check(request.url()); // refuse obviously bad links before queueing anything
        } catch (RecipeExtractionException e) {
            throw new AiProcessingException(e.getMessage());
        }
        AiJob job = jobs.submit(JobType.LINK, request.householdId(), request.userId(), request.url(),
                () -> links.propose(request.url(), request.servingsMade(), request.householdId(), request.userId()));
        return accepted(job);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobView> job(@PathVariable long id) {
        return store.find(id)
                .map(JobView::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public List<JobView> recentJobs(@RequestParam @Positive long householdId) {
        return store.recent(householdId).stream().map(JobView::of).toList();
    }

    @GetMapping("/price-estimate")
    public PriceEstimate priceEstimate(
            @RequestParam String dish,
            @RequestParam(defaultValue = "1") int servings,
            @RequestParam(required = false) String city) {
        return prices.estimate(dish, servings, city);
    }

    public record PriceEditRequest(@NotBlank String dish, String city, @NotNull BigDecimal pricePerServing) {
    }

    /** The user corrects an estimate; their price is kept for that dish from then on. */
    @PutMapping("/price-estimate")
    public PriceEstimate editPrice(@Valid @RequestBody PriceEditRequest request) {
        return prices.setUserPrice(request.dish(), request.city(), request.pricePerServing());
    }

    /** Which parts of the AI module can work on this machine right now. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("modelServer", llm.isAvailable());
        status.put("visionModel", properties.ollama().visionModel());
        status.put("textModel", properties.ollama().textModel());
        status.put("ocr", ocr.isAvailable());
        status.put("videoLinks", videos.isAvailable());
        status.put("webPriceSearch", webSearch.isConfigured());
        return status;
    }

    private static ResponseEntity<JobView> accepted(AiJob job) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobView.of(job));
    }

    /** Reads an upload and checks it really is an image before any work is queued. */
    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AiProcessingException("An image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AiProcessingException("Images can be at most 10 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AiProcessingException("The upload could not be read", e);
        }
        ImageInput.decode(bytes);
        return bytes;
    }
}
