package com.utp.assistant.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Correo ya obtenido (por ejemplo desde /api/gmail/messages/{id}) que se envía a Gemini para análisis.
 */
@Schema(description = "Correo a analizar. Puede copiarse de la respuesta de GET /api/gmail/messages/{id}.")
public record AnalyzeEmailRequest(
        @Schema(example = "1a0d9a1269fc854a") @Size(max = 128) String messageId,
        @Schema(example = "1a0d9a1269fc854a") @Size(max = 128) String threadId,
        @Schema(description = "Header From. Su email se usa como email real del contacto.",
                example = "Ana Torres <ana.torres@techcorp.com>") @NotBlank @Size(max = 512) String from,
        @Schema(example = "Reunión módulo de pagos") @Size(max = 1000) String subject,
        @Schema(description = "Header Date. Referencia para resolver fechas relativas (\"el lunes\").",
                example = "Fri, 25 Sep 2026 12:33:19 -0500") @NotBlank @Size(max = 128) String date,
        @Schema(example = "¿Podemos reunirnos el lunes a las 3:00 p.m.?") @NotBlank @Size(max = 50_000) String body) {
}
