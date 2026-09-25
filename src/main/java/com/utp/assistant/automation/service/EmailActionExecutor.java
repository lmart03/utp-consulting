package com.utp.assistant.automation.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.utp.assistant.assistant.service.AssistantTool;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.dto.CalendarEventResponse;
import com.utp.assistant.calendar.service.GoogleCalendarService;
import com.utp.assistant.crm.dto.ProspectResponse;
import com.utp.assistant.crm.service.CrmContactCommand;
import com.utp.assistant.crm.service.CrmService;
import com.utp.assistant.jira.dto.JiraIssueRequest;
import com.utp.assistant.jira.dto.JiraIssueResponse;
import com.utp.assistant.jira.service.JiraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ejecuta una EmailAction reutilizando CrmService, JiraService y GoogleCalendarService. Switch explícito sobre la allowlist:
 * los argumentos de Gemini solo se usan como datos, nunca como código.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailActionExecutor {

    /** Resultado exitoso: id externo (SCRUM-6 / eventId), respuesta a guardar, url externa y metadata para observabilidad. */
    public record Result(String externalId, Object response, String externalUrl, Map<String, Object> metadata) {
        public Result(String externalId, Object response) {
            this(externalId, response, null, Map.of());
        }
    }

    static final String JIRA_LABEL_PREFIX = "utp-gmail-";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JiraService jiraService;
    private final CrmService crmService;
    private final ProcessedEmailStore store;
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
            case ACTUALIZAR_CONTACTO_CRM -> upsertCrmContact(args, action, gmailMessageId);
        };
    }

    /**
     * CRM propio: upsert por email (idempotente por gmailMessageId, así que un reintento no necesita búsqueda previa).
     * El remitente real y si el correo pidió reunión se toman del correo registrado, no de Gemini.
     */
    private Result upsertCrmContact(Map<String, Object> args, EmailAction action, String gmailMessageId) {
        Long emailId = action.getProcessedEmail().getId();
        ProcessedEmail email = store.findById(emailId).orElseThrow();
        boolean meetingRequested = store.actions(emailId).stream()
                .anyMatch(a -> AssistantTool.AGENDAR_REUNION_GOOGLE_CALENDAR.functionName().equals(a.getToolName()));

        CrmService.UpsertResult result = crmService.upsertFromEmail(new CrmContactCommand(
                email.getFromAddress(),
                gmailMessageId,
                email.getSubject(),
                optional(args, "name"),
                optional(args, "email"),
                optional(args, "company"),
                optional(args, "phone"),
                optional(args, "status"),
                optional(args, "notes"),
                meetingRequested));

        ProspectResponse prospect = result.prospect();
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("prospectId", prospect.id());
        metadata.put("name", prospect.name());
        metadata.put("email", prospect.email());
        if (prospect.company() != null) {
            metadata.put("company", prospect.company());
        }
        metadata.put("status", prospect.status().name());
        metadata.put("created", result.created());
        metadata.put("missingFields", prospect.missingFields());
        metadata.put("inferredFields", prospect.inferredFields());
        return new Result(String.valueOf(prospect.id()), prospect, null, metadata);
    }

    private Result createJiraIssue(Map<String, Object> args, int attempt, String gmailMessageId) {
        String label = JIRA_LABEL_PREFIX + gmailMessageId;
        if (attempt > 1) {
            Optional<JiraIssueResponse> existing = jiraService.findIssueByLabel(label);
            if (existing.isPresent()) {
                log.info("Issue Jira {} ya existía para el correo {}; no se crea otra", existing.get().key(), gmailMessageId);
                String browseUrl = jiraService.browseUrl(existing.get().key());
                return new Result(existing.get().key(), existing.get(), browseUrl, Map.of());
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
        String browseUrl = jiraService.browseUrl(created.key());
        return new Result(created.key(), created, browseUrl, Map.of());
    }

    private Result createCalendarEvent(Map<String, Object> args, int attempt, String gmailMessageId, String principalName) {
        String accessToken = tokenService.getAccessTokenWithScope(principalName, GoogleCalendarService.CALENDAR_EVENTS_SCOPE);
        if (attempt > 1) {
            Optional<CalendarEventResponse> existing = calendarService.findEventByGmailMessageId(accessToken, gmailMessageId);
            if (existing.isPresent()) {
                log.info("Evento {} ya existía para el correo {}; no se crea otro", existing.get().eventId(), gmailMessageId);
                Map<String, Object> metadata = buildCalendarMetadata(existing.get().startDateTime(), existing.get().endDateTime());
                return new Result(existing.get().eventId(), existing.get(), existing.get().htmlLink(), metadata);
            }
        }

        CalendarEventRequest request = new CalendarEventRequest(
                required(args, "title"),
                optional(args, "description"),
                required(args, "startDateTime"),
                required(args, "endDateTime"),
                optional(args, "attendeeEmail"));
        CalendarEventResponse created = calendarService.createEvent(accessToken, request, gmailMessageId);
        Map<String, Object> metadata = buildCalendarMetadata(created.startDateTime(), created.endDateTime());
        return new Result(created.eventId(), created, created.htmlLink(), metadata);
    }

    private static Map<String, Object> buildCalendarMetadata(String start, String end) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (start != null) {
            metadata.put("start", start);
        }
        if (end != null) {
            metadata.put("end", end);
        }
        return metadata;
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
