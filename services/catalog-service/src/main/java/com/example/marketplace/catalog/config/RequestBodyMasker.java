package com.example.marketplace.catalog.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;

/**
 * Маскирует чувствительные поля в JSON (пароли, токены и т.п.).
 */
public final class RequestBodyMasker {

    private static final String MASK = "***";
    private static final String[] SENSITIVE_KEYS = {
            "password", "secret", "token", "authorization",
            "api_key", "apikey", "credentials", "credit_card"
    };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RequestBodyMasker() {
    }

    /**
     * Возвращает JSON-строку с замаскированными чувствительными полями.
     * Если вход не валидный JSON, возвращает "[non-json]".
     */
    public static String mask(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            maskNode(root);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "[non-json]";
        }
    }

    private static void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                String name = it.next();
                if (isSensitive(name)) {
                    obj.put(name, MASK);
                } else {
                    maskNode(obj.get(name));
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                maskNode(child);
            }
        }
    }

    private static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}
