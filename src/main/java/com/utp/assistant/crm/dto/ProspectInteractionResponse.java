package com.utp.assistant.crm.dto;

import java.time.OffsetDateTime;

import com.utp.assistant.crm.entity.DataSource;
import com.utp.assistant.crm.entity.ProspectInteraction;
import com.utp.assistant.crm.entity.ProspectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Interacción registrada en el historial del prospecto (una por correo).")
public record ProspectInteractionResponse(
        Long id,
        String gmailMessageId,
        String subject,
        ProspectStatus statusBefore,
        ProspectStatus statusAfter,
        String notes,
        @Schema(description = "GEMINI (automatización) o MANUAL.") DataSource source,
        OffsetDateTime createdAt) {

    public static ProspectInteractionResponse from(ProspectInteraction i) {
        return new ProspectInteractionResponse(i.getId(), i.getGmailMessageId(), i.getSubject(), i.getStatusBefore(),
                i.getStatusAfter(), i.getNotes(), i.getSource(), i.getCreatedAt());
    }
}
