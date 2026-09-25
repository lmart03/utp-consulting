package com.utp.assistant.automation.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.EmailActionStatus;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.automation.entity.ProcessedEmailStatus;
import com.utp.assistant.reply.dto.EmailReplyDto;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** DTOs de solo lectura para monitorear la automatización desde Swagger y Frontend Angular. */
public final class AutomationDtos {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AutomationDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AutomationStatus", description = "Estado del polling automático y conteo de correos por estado.")
    public record StatusDto(
            @Schema(description = "Scheduler activo (assistant.polling.enabled).", example = "true") boolean enabled,
            @Schema(example = "10000") long pollingIntervalMs,
            @Schema(description = "Cutoff: solo se procesan correos recibidos después de este momento.") Instant automationStartedAt,
            @Schema(example = "false") boolean processOldEmails,
            @Schema(description = "Cuenta Google usada por la automatización (null si nadie inició sesión).",
                    example = "lmartinezquijandria@gmail.com") String googleAccount,
            @Schema(description = "Hay un ciclo en ejecución ahora mismo.") boolean running,
            @Schema(description = "Resultado del último ciclo.") RunResultDto lastRun,
            long processing, long processed, long ignored, long partial, long failed,
            @Schema(description = "Total de correos procesados hoy.", example = "12") long totalToday) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AutomationRunResult", description = "Resultado de un ciclo de automatización.")
    public record RunResultDto(
            @Schema(description = "false si el ciclo no se ejecutó (otro en curso, falta login, etc.).") boolean executed,
            @Schema(example = "Ciclo completado.") String message,
            @Schema(description = "Correos nuevos reclamados y procesados.") int newEmails,
            @Schema(description = "Correos reintentados.") int retried,
            @Schema(description = "Candidatos omitidos (ya registrados o anteriores al cutoff).") int skipped,
            int errors,
            OffsetDateTime finishedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ProcessedEmail", description = "Correo registrado por la automatización (sin el cuerpo).")
    public record ProcessedEmailDto(
            Long id,
            @Schema(example = "1a0d9bc29aed0b7f") String gmailMessageId,
            String threadId,
            @Schema(example = "Ana Torres <ana.torres@techcorp.com>") String fromAddress,
            @Schema(example = "Reunión módulo de pagos TechCorp") String subject,
            OffsetDateTime receivedAt,
            @Schema(example = "PROCESSED") ProcessedEmailStatus status,
            int attemptCount,
            @Schema(description = "Próximo reintento (o lease mientras está PROCESSING).") OffsetDateTime nextRetryAt,
            String errorMessage,
            String aiSummary,
            String detectedCompany,
            @Schema(description = "Se quitó la etiqueta UNREAD en Gmail.") boolean gmailMarkedRead,
            OffsetDateTime processedAt,
            Long elapsedMs,
            @Schema(example = "SCRUM-14") String jiraIssueKey,
            @Schema(example = "2026-09-26T15:00:00-05:00") String meetingStart,
            @Schema(example = "2026-09-26T16:00:00-05:00") String meetingEnd,
            @Schema(description = "Id del contacto CRM creado/actualizado por este correo.", example = "3") Long crmProspectId,
            @Schema(example = "Carla Mendoza") String crmContactName,
            @Schema(example = "MEETING_SCHEDULED") String crmStatus,
            @Schema(description = "Id de la respuesta sugerida por la IA.", example = "5") Long replyId,
            @Schema(description = "Estado de la respuesta sugerida.", example = "DRAFT") ReplyStatus replyStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static ProcessedEmailDto from(ProcessedEmail e) {
            return from(e, List.of());
        }

        public static ProcessedEmailDto from(ProcessedEmail e, List<EmailAction> actions) {
            return from(e, actions, null);
        }

        public static ProcessedEmailDto from(ProcessedEmail e, List<EmailAction> actions, EmailReply reply) {
            String detectedCompany = extractCompany(actions);
            String jiraIssueKey = null;
            String meetingStart = null;
            String meetingEnd = null;
            Long crmProspectId = null;
            String crmContactName = null;
            String crmStatus = null;

            for (EmailAction a : actions) {
                if ("actualizar_contacto_crm".equals(a.getToolName()) && a.getStatus() == EmailActionStatus.SUCCESS) {
                    Map<String, Object> prospect = parseJson(a.getResponsePayload());
                    crmProspectId = a.getExternalId() != null && a.getExternalId().matches("[0-9]+") ? Long.valueOf(a.getExternalId()) : null;
                    crmContactName = prospect.get("name") != null ? String.valueOf(prospect.get("name")) : null;
                    crmStatus = prospect.get("status") != null ? String.valueOf(prospect.get("status")) : null;
                    if (detectedCompany == null && prospect.get("company") != null) {
                        detectedCompany = String.valueOf(prospect.get("company"));
                    }
                } else if ("crear_ticket_jira".equals(a.getToolName()) && a.getExternalId() != null) {
                    jiraIssueKey = a.getExternalId();
                } else if ("agendar_reunion_google_calendar".equals(a.getToolName())) {
                    Map<String, Object> resp = parseJson(a.getResponsePayload());
                    if (resp.containsKey("start")) {
                        meetingStart = String.valueOf(resp.get("start"));
                    }
                    if (resp.containsKey("end")) {
                        meetingEnd = String.valueOf(resp.get("end"));
                    }
                    if (meetingStart == null || meetingEnd == null) {
                        Map<String, Object> req = parseJson(a.getRequestPayload());
                        if (meetingStart == null && req.containsKey("startDateTime")) {
                            meetingStart = String.valueOf(req.get("startDateTime"));
                        }
                        if (meetingEnd == null && req.containsKey("endDateTime")) {
                            meetingEnd = String.valueOf(req.get("endDateTime"));
                        }
                    }
                }
            }

            Long elapsedMs = null;
            if (e.getProcessedAt() != null && e.getCreatedAt() != null) {
                elapsedMs = Math.max(0, Duration.between(e.getCreatedAt(), e.getProcessedAt()).toMillis());
            }

            return new ProcessedEmailDto(e.getId(), e.getGmailMessageId(), e.getThreadId(), e.getFromAddress(),
                    e.getSubject(), e.getReceivedAt(), e.getStatus(), e.getAttemptCount(), e.getNextRetryAt(),
                    e.getErrorMessage(), e.getAiSummary(), detectedCompany, e.isGmailMarkedRead(), e.getProcessedAt(),
                    elapsedMs, jiraIssueKey, meetingStart, meetingEnd, crmProspectId, crmContactName, crmStatus,
                    reply != null ? reply.getId() : null, reply != null ? reply.getStatus() : null,
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "EmailAction", description = "Herramienta solicitada por Gemini para un correo y su resultado.")
    public record EmailActionDto(
            Long id,
            @Schema(example = "crear_ticket_jira") String toolName,
            @Schema(example = "SUCCESS") EmailActionStatus status,
            @Schema(description = "Clave Jira o id del evento de Calendar.", example = "SCRUM-6") String externalId,
            @Schema(description = "URL hacia Jira o Google Calendar.", example = "https://utp-tics.atlassian.net/browse/SCRUM-6") String externalUrl,
            @Schema(description = "Argumentos de Gemini (JSON).") String requestPayload,
            @Schema(description = "Respuesta de Jira/Calendar (JSON).") String responsePayload,
            String errorMessage,
            int attemptCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static EmailActionDto from(EmailAction a) {
            return from(a, null);
        }

        public static EmailActionDto from(EmailAction a, String jiraBaseUrl) {
            String externalUrl = null;
            if (a.getExternalId() != null) {
                if ("crear_ticket_jira".equals(a.getToolName()) && jiraBaseUrl != null && !jiraBaseUrl.isBlank()) {
                    String cleanBase = jiraBaseUrl.strip().replaceAll("/+$", "");
                    externalUrl = cleanBase + "/browse/" + a.getExternalId();
                } else if ("agendar_reunion_google_calendar".equals(a.getToolName())) {
                    Map<String, Object> resp = parseJson(a.getResponsePayload());
                    if (resp.containsKey("htmlLink")) {
                        externalUrl = String.valueOf(resp.get("htmlLink"));
                    }
                }
            }
            return new EmailActionDto(a.getId(), a.getToolName(), a.getStatus(), a.getExternalId(),
                    externalUrl, a.getRequestPayload(), a.getResponsePayload(), a.getErrorMessage(), a.getAttemptCount(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ProcessedEmailDetail", description = "Correo procesado con todas sus acciones.")
    public record ProcessedEmailDetailDto(ProcessedEmailDto email, List<EmailActionDto> actions,
                                          @Schema(description = "Respuesta sugerida por la IA (si existe).") EmailReplyDto reply) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "IntegrationsStatus", description = "Estado visual de las integraciones de UTP Assistant.")
    public record IntegrationsStatusDto(
            IntegrationInfo gmail,
            IntegrationInfo gemini,
            IntegrationInfo jira,
            IntegrationInfo calendar,
            @Schema(description = "CRM propio en PostgreSQL.") IntegrationInfo crm
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IntegrationInfo(
            boolean connected,
            boolean configured,
            String account,
            String model,
            String projectKey,
            String baseUrl,
            String calendarId,
            String timeZone
    ) {
        public static IntegrationInfo ofGmail(boolean connected, String account) {
            return new IntegrationInfo(connected, connected, account, null, null, null, null, null);
        }

        public static IntegrationInfo ofGemini(boolean configured, String model) {
            return new IntegrationInfo(configured, configured, null, model, null, null, null, null);
        }

        public static IntegrationInfo ofJira(boolean configured, String projectKey, String baseUrl) {
            return new IntegrationInfo(configured, configured, null, null, projectKey, baseUrl, null, null);
        }

        public static IntegrationInfo ofCrm() {
            return new IntegrationInfo(true, true, null, null, null, null, null, null);
        }

        public static IntegrationInfo ofCalendar(boolean connected, String calendarId, String timeZone) {
            return new IntegrationInfo(connected, connected, null, null, null, null, calendarId, timeZone);
        }
    }

    private static String extractCompany(List<EmailAction> actions) {
        if (actions == null) {
            return null;
        }
        for (EmailAction action : actions) {
            if (action.getRequestPayload() != null && !action.getRequestPayload().isBlank()) {
                Map<String, Object> args = parseJson(action.getRequestPayload());
                Object company = args.get("company");
                if (company == null) {
                    company = args.get("empresa");
                }
                if (company != null && !company.toString().isBlank()) {
                    return company.toString().strip();
                }
            }
        }
        return null;
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
