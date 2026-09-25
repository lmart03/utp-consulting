package com.utp.assistant.assistant.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado del análisis: resumen en texto, function calls aceptados y nombres de funciones
 * rechazadas por no estar en la allowlist (se devuelven solo para inspección).
 */
@Schema(description = "Resultado del análisis de Gemini.")
public record GeminiEmailAnalysisResponse(
        @Schema(example = "Ana Torres de TechCorp quiere continuar con la propuesta del módulo de pagos y propone reunirse el lunes 28/09 a las 3 p.m.")
        String summary,
        @Schema(description = "Acciones permitidas que Gemini propone.") List<RequestedToolCall> toolCalls,
        @Schema(description = "Funciones fuera de la allowlist que Gemini intentó usar y fueron rechazadas.", example = "[]")
        List<String> rejectedToolCalls) {
}
