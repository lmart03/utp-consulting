package com.utp.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Evento a crear en Google Calendar. Las fechas van en ISO-8601 con offset, ej. 2026-09-28T15:00:00-05:00.
 */
@Schema(description = "Evento a crear en Google Calendar (zona horaria America/Lima).")
public record CalendarEventRequest(
        @Schema(example = "Reunión módulo de pagos - TechCorp") @NotBlank @Size(max = 500) String title,
        @Schema(example = "Reunión para discutir el módulo de pagos.") @Size(max = 8000) String description,
        @Schema(description = "Inicio en ISO-8601.", example = "2026-09-28T15:00:00-05:00") @NotBlank String startDateTime,
        @Schema(description = "Fin en ISO-8601; debe ser posterior al inicio.", example = "2026-09-28T16:00:00-05:00")
        @NotBlank String endDateTime,
        @Schema(description = "Invitado opcional.", example = "cliente@gmail.com") @Email String attendeeEmail) {
}
