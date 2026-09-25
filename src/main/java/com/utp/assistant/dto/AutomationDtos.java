package com.utp.assistant.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.utp.assistant.entity.EmailAction;
import com.utp.assistant.entity.EmailActionStatus;
import com.utp.assistant.entity.ProcessedEmail;
import com.utp.assistant.entity.ProcessedEmailStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** DTOs de solo lectura para monitorear la automatización desde Swagger. */
public final class AutomationDtos {

    private AutomationDtos() {
    }

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
            long processing, long processed, long ignored, long partial, long failed) {
    }

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
            @Schema(description = "Se quitó la etiqueta UNREAD en Gmail.") boolean gmailMarkedRead,
            OffsetDateTime processedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static ProcessedEmailDto from(ProcessedEmail e) {
            return new ProcessedEmailDto(e.getId(), e.getGmailMessageId(), e.getThreadId(), e.getFromAddress(),
                    e.getSubject(), e.getReceivedAt(), e.getStatus(), e.getAttemptCount(), e.getNextRetryAt(),
                    e.getErrorMessage(), e.getAiSummary(), e.isGmailMarkedRead(), e.getProcessedAt(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    @Schema(name = "EmailAction", description = "Herramienta solicitada por Gemini para un correo y su resultado.")
    public record EmailActionDto(
            Long id,
            @Schema(example = "crear_ticket_jira") String toolName,
            @Schema(example = "SUCCESS") EmailActionStatus status,
            @Schema(description = "Clave Jira o id del evento de Calendar.", example = "SCRUM-6") String externalId,
            @Schema(description = "Argumentos de Gemini (JSON).") String requestPayload,
            @Schema(description = "Respuesta de Jira/Calendar (JSON).") String responsePayload,
            String errorMessage,
            int attemptCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static EmailActionDto from(EmailAction a) {
            return new EmailActionDto(a.getId(), a.getToolName(), a.getStatus(), a.getExternalId(),
                    a.getRequestPayload(), a.getResponsePayload(), a.getErrorMessage(), a.getAttemptCount(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    @Schema(name = "ProcessedEmailDetail", description = "Correo procesado con todas sus acciones.")
    public record ProcessedEmailDetailDto(ProcessedEmailDto email, List<EmailActionDto> actions) {
    }
}
