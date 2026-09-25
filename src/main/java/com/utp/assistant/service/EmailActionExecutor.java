package com.utp.assistant.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.utp.assistant.dto.CalendarEventRequest;
import com.utp.assistant.dto.CalendarEventResponse;
import com.utp.assistant.dto.JiraIssueRequest;
import com.utp.assistant.dto.JiraIssueResponse;
import com.utp.assistant.entity.EmailAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ejecuta una EmailAction reutilizando JiraService y GoogleCalendarService. Switch explícito sobre la allowlist:
 * los argumentos de Gemini solo se usan como datos, nunca como código.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailActionExecutor {

    /** Resultado exitoso: id externo (SCRUM-6 / eventId) y respuesta a guardar. */
    public record Result(String externalId, Object response) {
    }

    static final String JIRA_LABEL_PREFIX = "utp-gmail-";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JiraService jiraService;
    private final GoogleCalendarService calendarService;
    private final GoogleTokenService tokenService;

    /**
     * @param attempt número de intento de esta acción (1 = primero). Desde el segundo intento se busca antes si
     *                la acción ya se había creado (p. ej. timeout tras crearla) para no duplicarla.
     */
    public Result execute(EmailAction action, int attempt, String gmailMessageId, String principalName) {
        AssistantTool tool = AssistantTool.fromFunctionName(action.getToolName())
                .orElseThrow(() -> new IllegalStateException("Herramienta no permitida: " + action.getToolName()));
        Map<String, Object> args = parseArguments(action.getRequestPayload());

        return switch (tool) {
            case CREAR_TICKET_JIRA -> createJiraIssue(args, attempt, gmailMessageId);
            case AGENDAR_REUNION_GOOGLE_CALENDAR -> createCalendarEvent(args, attempt, gmailMessageId, principalName);
            case ACTUALIZAR_CONTACTO_CRM -> throw new IllegalStateException(ProcessedEmailStore.CRM_NOT_IMPLEMENTED);
        };
    }

    private Result createJiraIssue(Map<String, Object> args, int attempt, String gmailMessageId) {
        String label = JIRA_LABEL_PREFIX + gmailMessageId;
        if (attempt > 1) {
            Optional<JiraIssueResponse> existing = jiraService.findIssueByLabel(label);
            if (existing.isPresent()) {
                log.info("Issue Jira {} ya existía para el correo {}; no se crea otra", existing.get().key(), gmailMessageId);
                return new Result(existing.get().key(), existing.get());
            }
        }

        // La prioridad sugerida por Gemini va en la descripción: si Jira no reconociera el nombre, el ticket fallaría.
        StringBuilder description = new StringBuilder(required(args, "description"));
        String priority = optional(args, "priority");
        if (priority != null) {
            description.append("\n\nPrioridad sugerida: ").append(priority);
        }
        description.append("\n\nUTP-GMAIL-ID: ").append(gmailMessageId);

        JiraIssueResponse created = jiraService.createIssue(
                new JiraIssueRequest(required(args, "summary"), description.toString(), null), List.of(label));
        return new Result(created.key(), created);
    }

    private Result createCalendarEvent(Map<String, Object> args, int attempt, String gmailMessageId, String principalName) {
        String accessToken = tokenService.getAccessTokenWithScope(principalName, GoogleCalendarService.CALENDAR_EVENTS_SCOPE);
        if (attempt > 1) {
            Optional<CalendarEventResponse> existing = calendarService.findEventByGmailMessageId(accessToken, gmailMessageId);
            if (existing.isPresent()) {
                log.info("Evento {} ya existía para el correo {}; no se crea otro", existing.get().eventId(), gmailMessageId);
                return new Result(existing.get().eventId(), existing.get());
            }
        }

        CalendarEventRequest request = new CalendarEventRequest(
                required(args, "title"),
                optional(args, "description"),
                required(args, "startDateTime"),
                required(args, "endDateTime"),
                optional(args, "attendeeEmail"));
        CalendarEventResponse created = calendarService.createEvent(accessToken, request, gmailMessageId);
        return new Result(created.eventId(), created);
    }

    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private static String required(Map<String, Object> args, String name) {
        String value = optional(args, name);
        if (value == null) {
            throw new IllegalArgumentException("Gemini no envió el argumento requerido '" + name + "'.");
        }
        return value;
    }

    private static String optional(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }
}
