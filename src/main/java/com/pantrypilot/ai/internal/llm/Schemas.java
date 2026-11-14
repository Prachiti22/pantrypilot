package com.pantrypilot.ai.internal.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for writing JSON Schemas as maps. */
public final class Schemas {

    private Schemas() {
    }

    /** An object where every listed property is required. Pass name, schema, name, schema, ... */
    public static Map<String, Object> object(Object... namesAndSchemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < namesAndSchemas.length; i += 2) {
            properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }

    public static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    public static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    public static Map<String, Object> number() {
        return Map.of("type", "number");
    }

    public static Map<String, Object> bool() {
        return Map.of("type", "boolean");
    }

    public static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }
}
