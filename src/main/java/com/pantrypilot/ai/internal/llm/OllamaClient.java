package com.pantrypilot.ai.internal.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.ai.internal.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calls a locally running Ollama server over HTTP with {@link HttpClient}.
 * Photos therefore never leave the machine the server runs on.
 *
 * <p>A semaphore limits how many requests are in flight, because a laptop can
 * only run one or two model evaluations at a time and extra requests would just
 * time out waiting inside Ollama.
 */
@Component
public class OllamaClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final AiProperties.Ollama config;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Semaphore permits;

    public OllamaClient(AiProperties properties, ObjectMapper mapper) {
        this.config = properties.ollama();
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.permits = new Semaphore(Math.max(1, config.maxConcurrent()), true);
    }

    @Override
    public JsonNode generate(LlmRequest request) {
        String model = request.kind() == LlmRequest.Kind.VISION ? config.visionModel() : config.textModel();
        String body = toJson(buildBody(model, request));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/chat"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the model", e);
        }
        try {
            long start = System.nanoTime();
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            log.debug("Ollama {} answered in {} ms", model, (System.nanoTime() - start) / 1_000_000);
        } catch (IOException e) {
            throw new LlmException("Could not reach the model server at " + config.baseUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the model", e);
        } finally {
            permits.release();
        }

        if (response.statusCode() != 200) {
            throw new LlmException("Model server returned HTTP " + response.statusCode()
                    + " for model " + model + ": " + abbreviate(response.body()));
        }
        try {
            String content = mapper.readTree(response.body()).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new LlmException("Model " + model + " returned an empty answer");
            }
            return mapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new LlmException("Model " + model + " did not return valid JSON", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> buildBody(String model, LlmRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.system()));
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", request.prompt());
        if (!request.images().isEmpty()) {
            user.put("images", request.images().stream()
                    .map(Base64.getEncoder()::encodeToString)
                    .toList());
        }
        messages.add(user);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("think", false);
        body.put("format", request.schema());
        body.put("options", Map.of("temperature", 0));
        body.put("messages", messages);
        return body;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new LlmException("Could not build the model request", e);
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
