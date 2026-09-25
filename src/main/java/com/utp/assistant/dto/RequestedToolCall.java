package com.utp.assistant.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Function call solicitado por Gemini (nombre ya validado contra la allowlist). NO se ejecuta. */
@Schema(description = "Acción propuesta por Gemini. Solo se informa; no se ejecuta.")
public record RequestedToolCall(
        @Schema(allowableValues = {"actualizar_contacto_crm", "crear_ticket_jira", "agendar_reunion_google_calendar"},
                example = "agendar_reunion_google_calendar") String name,
        @Schema(description = "Argumentos estructurados generados por Gemini.",
                example = "{\"title\":\"Reunión módulo de pagos - TechCorp\",\"startDateTime\":\"2026-09-28T15:00:00-05:00\","
                        + "\"endDateTime\":\"2026-09-28T16:00:00-05:00\",\"attendeeEmail\":\"ana.torres@techcorp.com\"}")
        Map<String, Object> arguments) {
}
