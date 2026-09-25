package com.utp.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Correo de Gmail con el cuerpo extraído como texto.")
public record GmailMessageDto(
        @Schema(example = "1a0d9a1269fc854a") String id,
        @Schema(example = "1a0d9a1269fc854a") String threadId,
        @Schema(example = "Ana Torres <ana.torres@techcorp.com>") String from,
        @Schema(example = "Reunión módulo de pagos") String subject,
        @Schema(description = "Header Date original del correo.", example = "Fri, 25 Sep 2026 12:33:19 -0500") String date,
        @Schema(description = "Vista previa generada por Gmail.", example = "Hola equipo de UTP Consult, Somos TechCorp...") String snippet,
        @Schema(description = "Cuerpo en texto plano (text/plain o HTML convertido).") String body) {
}
