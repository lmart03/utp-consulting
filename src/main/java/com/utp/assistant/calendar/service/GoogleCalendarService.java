package com.utp.assistant.calendar.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.utp.assistant.auth.exception.GmailAuthorizationException;
import com.utp.assistant.auth.exception.GoogleScopeMissingException;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.calendar.config.CalendarProperties;
import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.dto.CalendarEventResponse;
import com.utp.assistant.calendar.exception.CalendarApiException;
import com.utp.assistant.calendar.exception.InvalidCalendarEventException;
import com.utp.assistant.gmail.config.GmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Crea eventos reales en Google Calendar de la cuenta autenticada, con el access token gestionado por Spring Security.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    public static final String CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events";
    /** Clave de extendedProperties.private que vincula el evento con el correo de origen (idempotencia). */
    static final String GMAIL_MESSAGE_ID_PROPERTY = "gmailMessageId";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private final GoogleTokenService tokenService;
    private final CalendarProperties properties;
    private final GmailProperties gmailProperties;

    public CalendarEventResponse createEvent(Authentication authentication, CalendarEventRequest request) {
        return createEvent(tokenService.getAccessTokenWithScope(authentication, CALENDAR_EVENTS_SCOPE), request, null);
    }

    /**
     * Crea el evento con un access token explícito (automatización). Si se indica gmailMessageId se guarda en
     * extendedProperties.private para poder detectar el evento si hay que reintentar.
     */
    public CalendarEventResponse createEvent(String accessToken, CalendarEventRequest request, String gmailMessageId) {
        Event event = buildEvent(request);
        if (gmailMessageId != null) {
            event.setExtendedProperties(new Event.ExtendedProperties()
                    .setPrivate(Map.of(GMAIL_MESSAGE_ID_PROPERTY, gmailMessageId)));
        }
        Calendar calendar = buildClient(accessToken);

        Event created;
        try {
            created = calendar.events().insert(properties.calendarId(), event)
                    .setSendUpdates(properties.sendUpdates())
                    .execute();
        } catch (IOException ex) {
            throw translate(ex);
        }
        if (created == null || created.getId() == null) {
            throw new CalendarApiException("Google Calendar devolvió una respuesta inválida al crear el evento.");
        }

        log.info("Evento creado en Google Calendar: id={}, inicio={}", created.getId(), request.startDateTime());
        return toResponse(created);
    }

    /** Busca un evento creado previamente para ese correo (extendedProperties.private.gmailMessageId). */
    public Optional<CalendarEventResponse> findEventByGmailMessageId(String accessToken, String gmailMessageId) {
        try {
            Events events = buildClient(accessToken).events().list(properties.calendarId())
                    .setPrivateExtendedProperty(List.of(GMAIL_MESSAGE_ID_PROPERTY + "=" + gmailMessageId))
                    .setShowDeleted(false)
                    .setMaxResults(1)
                    .execute();
            if (events == null || events.getItems() == null || events.getItems().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toResponse(events.getItems().getFirst()));
        } catch (IOException ex) {
            throw translate(ex);
        }
    }

    private static CalendarEventResponse toResponse(Event event) {
        return new CalendarEventResponse(
                event.getId(),
                event.getHtmlLink(),
                event.getStatus(),
                event.getSummary(),
                formatDateTime(event.getStart()),
                formatDateTime(event.getEnd()));
    }

    /** Valida las fechas y arma el Event. Sin llamadas a Google. */
    Event buildEvent(CalendarEventRequest request) {
        OffsetDateTime start = parseDateTime("startDateTime", request.startDateTime());
        OffsetDateTime end = parseDateTime("endDateTime", request.endDateTime());
        if (!end.isAfter(start)) {
            throw new InvalidCalendarEventException("endDateTime debe ser posterior a startDateTime.");
        }

        String timeZone = properties.timeZone().getId();
        Event event = new Event()
                .setSummary(request.title())
                .setDescription(request.description())
                .setStart(new EventDateTime().setDateTime(toGoogleDateTime(start)).setTimeZone(timeZone))
                .setEnd(new EventDateTime().setDateTime(toGoogleDateTime(end)).setTimeZone(timeZone));
        if (request.attendeeEmail() != null && !request.attendeeEmail().isBlank()) {
            event.setAttendees(List.of(new EventAttendee().setEmail(request.attendeeEmail().strip())));
        }
        return event;
    }

    /** ISO-8601 con offset; si no trae offset se interpreta en el timezone de negocio (America/Lima). */
    private OffsetDateTime parseDateTime(String field, String value) {
        try {
            return OffsetDateTime.parse(value.strip());
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(value.strip()).atZone(properties.timeZone()).toOffsetDateTime();
            } catch (DateTimeParseException ex) {
                throw new InvalidCalendarEventException(field + " no es una fecha ISO-8601 válida (ej. 2026-09-28T15:00:00-05:00).", ex);
            }
        }
    }

    private static DateTime toGoogleDateTime(OffsetDateTime dateTime) {
        return DateTime.parseRfc3339(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static String formatDateTime(EventDateTime eventDateTime) {
        if (eventDateTime == null || eventDateTime.getDateTime() == null) {
            return null;
        }
        return eventDateTime.getDateTime().toStringRfc3339();
    }

    private Calendar buildClient(String accessToken) {
        HttpRequestInitializer initializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
        return new Calendar.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), initializer)
                .setApplicationName(gmailProperties.applicationName())
                .build();
    }

    private static RuntimeException translate(IOException ex) {
        if (!(ex instanceof GoogleJsonResponseException responseException)) {
            return new CalendarApiException("No se pudo comunicar con Google Calendar API.", ex);
        }
        int status = responseException.getStatusCode();
        GoogleJsonError details = responseException.getDetails();
        String message = String.valueOf(details != null ? details.getMessage() : responseException.getStatusMessage());
        String reason = details != null && details.getErrors() != null && !details.getErrors().isEmpty()
                ? details.getErrors().getFirst().getReason() : "";
        log.warn("Google Calendar API respondió {} ({}): {}", status, reason, message);

        if (status == 401) {
            return new GmailAuthorizationException("Google rechazó el access token. Vuelve a iniciar sesión.", ex);
        }
        if (status == 403 && ("insufficientPermissions".equals(reason) || message.contains("insufficient authentication scopes"))) {
            return new GoogleScopeMissingException("El token no tiene permiso para Google Calendar. "
                    + "Vuelve a autorizar la aplicación en /oauth2/authorization/google.", ex);
        }
        if (status == 400) {
            return new InvalidCalendarEventException("Google Calendar rechazó el evento: " + message, ex);
        }
        return new CalendarApiException("Google Calendar API respondió " + status + ": " + message, ex);
    }
}
