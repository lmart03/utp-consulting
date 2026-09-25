package com.utp.assistant.calendar.controller;

import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.dto.CalendarEventResponse;
import com.utp.assistant.calendar.service.GoogleCalendarService;
import com.utp.assistant.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_CALENDAR)
@SecurityRequirement(name = OpenApiConfig.GOOGLE_SESSION)
public class CalendarController {

    private static final String EXAMPLE_EVENT = """
            {
              "title": "Reunión módulo de pagos - TechCorp",
              "description": "Reunión para discutir el módulo de pagos.",
              "startDateTime": "2026-09-28T15:00:00-05:00",
              "endDateTime": "2026-09-28T16:00:00-05:00",
              "attendeeEmail": "cliente@gmail.com"
            }""";

    private final GoogleCalendarService calendarService;

    @Operation(summary = "Crear evento en Google Calendar",
            description = "Crea un evento real en el calendario principal de la cuenta Google con sesión activa "
                    + "(zona America/Lima). Requiere haber iniciado sesión en /oauth2/authorization/google.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CalendarEventRequest.class),
                    examples = @ExampleObject(name = "Reunión con cliente", value = EXAMPLE_EVENT))))
    @ApiResponse(responseCode = "201", description = "Evento creado.")
    @ApiResponse(responseCode = "400", description = "Datos inválidos: fechas mal formadas o fin anterior al inicio.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sin sesión de Google.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Falta el permiso de Calendar (volver a autorizar).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "Error de Google Calendar API.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createEvent(@Valid @RequestBody CalendarEventRequest request,
                                             Authentication authentication) {
        return calendarService.createEvent(authentication, request);
    }
}
