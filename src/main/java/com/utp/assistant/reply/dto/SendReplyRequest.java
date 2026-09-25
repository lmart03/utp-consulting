package com.utp.assistant.reply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Texto final a enviar (el borrador tal cual o editado por el usuario).")
public record SendReplyRequest(
        @Schema(example = "Hola Ana,\n\nGracias por escribirnos...") @NotBlank @Size(max = 20000) String body) {
}
