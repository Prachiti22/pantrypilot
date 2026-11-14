package com.pantrypilot.ai.internal.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** A language model that answers in JSON. The only implementation talks to a local Ollama server. */
public interface LlmClient {

    /**
     * Sends the request and returns the model's answer parsed as JSON.
     *
     * @throws LlmException when the model is unreachable, times out or does not return JSON
     */
    JsonNode generate(LlmRequest request);

    /** Quick check that the model server answers. Never throws. */
    boolean isAvailable();
}
