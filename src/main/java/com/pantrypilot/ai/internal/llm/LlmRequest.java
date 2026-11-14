package com.pantrypilot.ai.internal.llm;

import java.util.List;
import java.util.Map;

/**
 * One structured-output request to a language model.
 *
 * @param images JPEG or PNG bytes; only used with {@link Kind#VISION}
 * @param schema JSON Schema the answer must follow
 */
public record LlmRequest(Kind kind, String system, String prompt, List<byte[]> images, Map<String, Object> schema) {

    public enum Kind {
        TEXT, VISION
    }

    public static LlmRequest text(String system, String prompt, Map<String, Object> schema) {
        return new LlmRequest(Kind.TEXT, system, prompt, List.of(), schema);
    }

    public static LlmRequest vision(String system, String prompt, byte[] image, Map<String, Object> schema) {
        return new LlmRequest(Kind.VISION, system, prompt, List.of(image), schema);
    }
}
