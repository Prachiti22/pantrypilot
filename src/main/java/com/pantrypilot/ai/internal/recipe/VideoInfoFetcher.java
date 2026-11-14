package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.shared.RecipeExtractionException;

/** Gets the text that comes with a video. An interface so tests need no network. */
public interface VideoInfoFetcher {

    /**
     * @param transcript captions as plain text; empty when the video has none or they were not requested
     */
    record VideoInfo(String title, String description, String transcript) {
    }

    /**
     * @param withTranscript also download captions, which is slower
     * @throws RecipeExtractionException when the video cannot be read
     */
    VideoInfo fetch(String url, boolean withTranscript);

    /** True when the tool this fetcher relies on is installed. Never throws. */
    boolean isAvailable();
}
