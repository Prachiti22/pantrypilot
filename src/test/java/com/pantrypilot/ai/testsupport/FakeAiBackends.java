package com.pantrypilot.ai.testsupport;

import com.pantrypilot.ai.internal.ocr.OcrEngine;
import com.pantrypilot.ai.internal.recipe.PageFetcher;
import com.pantrypilot.ai.internal.recipe.VideoInfoFetcher;
import com.pantrypilot.shared.RecipeExtractionException;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces everything that leaves the JVM (Ollama, Tesseract, the web, yt-dlp)
 * with in-memory fakes, so the Spring tests run anywhere with no setup.
 */
@TestConfiguration
public class FakeAiBackends {

    /** Pages the fake web serves, by URL. */
    public static final Map<String, String> PAGES = new ConcurrentHashMap<>();
    /** What the fake OCR engine reads from every image. */
    public static volatile String ocrText = "";

    @Bean
    @Primary
    public ScriptedLlm scriptedLlm() {
        return new ScriptedLlm();
    }

    @Bean
    @Primary
    public OcrEngine fakeOcr() {
        return new OcrEngine() {
            @Override
            public String read(BufferedImage image) {
                return ocrText;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    @Bean
    @Primary
    public PageFetcher fakePages() {
        return url -> {
            String html = PAGES.get(url);
            if (html == null) {
                throw new RecipeExtractionException("That website could not be reached");
            }
            return new PageFetcher.Page(url, html);
        };
    }

    @Bean
    @Primary
    public VideoInfoFetcher fakeVideos() {
        return new VideoInfoFetcher() {
            @Override
            public VideoInfo fetch(String url, boolean withTranscript) {
                throw new RecipeExtractionException("That video could not be read");
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
    }
}
