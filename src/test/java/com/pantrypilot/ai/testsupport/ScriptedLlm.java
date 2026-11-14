package com.pantrypilot.ai.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.ai.internal.llm.LlmClient;
import com.pantrypilot.ai.internal.llm.LlmException;
import com.pantrypilot.ai.internal.llm.LlmRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** A stand-in for the language model that answers from a function, so tests need no Ollama. */
public class ScriptedLlm implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile Function<LlmRequest, String> script = request -> {
        throw new LlmException("no model in this test");
    };
    private final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

    /** Every request is answered with the JSON text the function returns. */
    public void answerWith(Function<LlmRequest, String> script) {
        this.script = script;
    }

    public void answer(String json) {
        answerWith(request -> json);
    }

    public void unavailable() {
        answerWith(request -> {
            throw new LlmException("model is down");
        });
    }

    public List<LlmRequest> requests() {
        return requests;
    }

    public void reset() {
        requests.clear();
        unavailable();
    }

    @Override
    public JsonNode generate(LlmRequest request) {
        requests.add(request);
        try {
            return MAPPER.readTree(script.apply(request));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new LlmException("bad test JSON", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
