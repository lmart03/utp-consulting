package com.utp.assistant.config;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        List<String> fallbackModels,
        int retryAttempts,
        int timeoutMs,
        ZoneId businessTimeZone,
        Resource systemInstruction) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Modelo principal seguido de los de respaldo, sin vacíos ni duplicados. */
    public List<String> modelsInPriorityOrder() {
        List<String> models = new ArrayList<>();
        models.add(model);
        if (fallbackModels != null) {
            fallbackModels.stream()
                    .filter(candidate -> candidate != null && !candidate.isBlank() && !models.contains(candidate))
                    .forEach(models::add);
        }
        return List.copyOf(models);
    }

    /** Evita que la API key aparezca si alguien imprime las propiedades. */
    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + ", fallbackModels=" + fallbackModels + ", timeoutMs=" + timeoutMs
                + ", businessTimeZone=" + businessTimeZone + ", apiKey=" + (isConfigured() ? "****" : "<vacía>") + "]";
    }
}
