package com.utp.assistant.reply.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EmailReply", description = "Respuesta sugerida por la IA para un correo procesado.")
public record EmailReplyDto(
        Long id,
        Long processedEmailId,
        @Schema(example = "ana.torres@techcorp.com") String toAddress,
        @Schema(example = "Re: Reunión módulo de pagos") String subject,
        @Schema(description = "Texto actual del borrador (o el enviado).") String body,
        @Schema(description = "El usuario modificó el texto generado por la IA.") boolean edited,
        @Schema(example = "DRAFT") ReplyStatus status,
        String errorMessage,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static EmailReplyDto from(EmailReply r) {
        boolean edited = r.getAiBody() != null && r.getBody() != null && !r.getAiBody().strip().equals(r.getBody().strip());
        return new EmailReplyDto(r.getId(), r.getProcessedEmailId(), r.getToAddress(), r.getSubject(), r.getBody(),
                edited, r.getStatus(), r.getErrorMessage(), r.getSentAt(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
