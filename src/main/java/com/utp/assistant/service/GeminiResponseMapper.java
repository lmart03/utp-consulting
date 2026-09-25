package com.utp.assistant.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.utp.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.dto.RequestedToolCall;
import com.utp.assistant.exception.GeminiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Convierte la respuesta estructurada del SDK en GeminiEmailAnalysisResponse.
 * Los function calls se leen de Part.functionCall() (objetos del SDK, sin parsear texto)
 * y solo se aceptan los nombres de la allowlist {@link AssistantTool}.
 */
@Slf4j
@Component
public class GeminiResponseMapper {

    public GeminiEmailAnalysisResponse toAnalysis(GenerateContentResponse response) {
        if (response == null) {
            throw new GeminiApiException(HttpStatus.BAD_GATEWAY, "Gemini devolvió una respuesta vacía.");
        }

        List<Candidate> candidates = response.candidates().orElse(List.of());
        if (candidates.isEmpty()) {
            String reason = response.promptFeedback()
                    .flatMap(feedback -> feedback.blockReason())
                    .map(Object::toString)
                    .orElse("sin candidatos");
            throw new GeminiApiException(HttpStatus.BAD_GATEWAY, "Gemini no generó respuesta: " + reason + ".");
        }

        List<Part> parts = candidates.getFirst().content().flatMap(Content::parts).orElse(List.of());

        StringBuilder summary = new StringBuilder();
        List<RequestedToolCall> toolCalls = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (Part part : parts) {
            if (part.functionCall().isPresent()) {
                FunctionCall call = part.functionCall().get();
                String name = call.name().orElse("");
                if (AssistantTool.fromFunctionName(name).isPresent()) {
                    toolCalls.add(new RequestedToolCall(name, copyArguments(call.args().orElse(Map.of()))));
                } else {
                    log.warn("Gemini solicitó una función fuera de la allowlist y se rechazó: '{}'", name);
                    rejected.add(name);
                }
            } else if (part.text().isPresent() && !part.thought().orElse(false)) {
                summary.append(part.text().get());
            }
        }

        return new GeminiEmailAnalysisResponse(summary.toString().strip(), List.copyOf(toolCalls), List.copyOf(rejected));
    }

    private static Map<String, Object> copyArguments(Map<String, Object> args) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
