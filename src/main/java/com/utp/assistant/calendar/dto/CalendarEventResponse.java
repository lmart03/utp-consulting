package com.utp.assistant.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Evento creado en Google Calendar.")
public record CalendarEventResponse(
        @Schema(example = "k2l8m3n4o5p6q7r8s9t0") String eventId,
        @Schema(description = "Enlace para abrir el evento en Google Calendar.",
                example = "https://www.google.com/calendar/event?eid=azJsOG0z...") String htmlLink,
        @Schema(example = "confirmed") String status,
        @Schema(example = "Reunión módulo de pagos - TechCorp") String summary,
        @Schema(example = "2026-09-28T15:00:00.000-05:00") String startDateTime,
        @Schema(example = "2026-09-28T16:00:00.000-05:00") String endDateTime) {
}
