package com.pantrypilot.ai.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the AI module, bound from {@code pantrypilot.ai.*} in application.yml. */
@ConfigurationProperties(prefix = "pantrypilot.ai")
public record AiProperties(Ollama ollama, Ocr ocr, Jobs jobs, Links links, Price price) {

    /**
     * @param maxConcurrent how many requests may be sent to Ollama at once; a laptop runs one model at a time
     */
    public record Ollama(String baseUrl, String visionModel, String textModel, Duration timeout, int maxConcurrent) {
    }

    /**
     * @param dataPath    folder holding {@code eng.traineddata}; blank means look in the usual places
     * @param libraryPath folder holding the native Tesseract library; blank means look in the usual places
     * @param binarize    apply Otsu thresholding before OCR
     */
    public record Ocr(String dataPath, String libraryPath, String language, boolean binarize) {
    }

    public record Jobs(int workers, int queueCapacity, Duration timeout) {
    }

    /**
     * @param allowPrivateHosts let recipe links point at localhost and private networks; only for tests
     */
    public record Links(boolean allowPrivateHosts, Duration timeout, int maxBytes, String ytDlpPath) {
    }

    /**
     * @param searxngUrl base URL of a SearXNG instance; blank turns web price search off
     */
    public record Price(String searxngUrl, String defaultCity, String currency) {
    }
}
