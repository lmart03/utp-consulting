package com.utp.assistant.automation.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.utp.assistant.assistant.dto.AnalyzeEmailRequest;
import com.utp.assistant.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.assistant.dto.RequestedToolCall;
import com.utp.assistant.assistant.service.GeminiService;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.automation.config.AssistantProperties;
import com.utp.assistant.automation.dto.AutomationDtos.ProcessedEmailDto;
import com.utp.assistant.automation.dto.AutomationEvent;
import com.utp.assistant.automation.dto.AutomationEventStatus;
import com.utp.assistant.automation.dto.AutomationStage;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.EmailActionStatus;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.automation.entity.ProcessedEmailStatus;
import com.utp.assistant.calendar.config.CalendarProperties;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.service.GmailService;
import com.utp.assistant.gmail.service.ReceivedEmail;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import com.utp.assistant.reply.service.ReplyDraftCommand;
import com.utp.assistant.reply.service.ReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Procesa un correo ya reclamado: Gemini → registrar acciones → ejecutar solo las pendientes/fallidas →
 * estado final → borrador de respuesta → marcar leído en Gmail solo si todo terminó bien. Emite eventos de observabilidad en cada etapa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailProcessor {

    /** Acciones que se (re)ejecutan. SUCCESS y SKIPPED nunca se repiten. */
    private static final Set<EmailActionStatus> EXECUTABLE =
            EnumSet.of(EmailActionStatus.PENDING, EmailActionStatus.FAILED, EmailActionStatus.PROCESSING);
    private static final int MAX_BODY_CHARS = 50_000;

    private final GmailService gmailService;
    private final GeminiService geminiService;
    private final EmailActionExecutor actionExecutor;
    private final ProcessedEmailStore store;
    private final GoogleTokenService tokenService;
    private final AssistantProperties properties;
    private final AutomationEventPublisher publisher;
    private final ReplyService replyService;
    private final CalendarProperties calendarProperties;

    /**
     * @param received contenido del correo si ya se leyó en este ciclo; null en reintentos (se vuelve a leer de
     *                 Gmail solo si Gemini aún no lo había analizado, ya que el cuerpo no se guarda en BD).
     */
    public ProcessedEmail process(ProcessedEmail email, ReceivedEmail received, String principalName) {
        long startNano = System.nanoTime();
        String messageId = email.getGmailMessageId();
        String detectedCompany = null;

        if (email.getAnalyzedAt() == null) {
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(messageId)
                    .subject(email.getSubject())
                    .from(email.getFromAddress())
                    .stage(AutomationStage.AI_ANALYSIS_STARTED)
                    .status(AutomationEventStatus.PROCESSING)
                    .message("Analizando correo con Gemini")
                    .build());

            try {
                ReceivedEmail content = received != null ? received
                        : gmailService.getReceivedEmail(gmailToken(principalName), messageId);
                GeminiEmailAnalysisResponse analysis = geminiService.analyzeEmail(toAnalyzeRequest(content.message()));
                detectedCompany = extractDetectedCompany(analysis.toolCalls());

                store.recordAnalysis(email.getId(), analysis.summary(), analysis.toolCalls(), analysis.rejectedToolCalls());

                int toolsCount = analysis.toolCalls().size();
                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(messageId)
                        .subject(email.getSubject())
                        .from(email.getFromAddress())
                        .detectedCompany(detectedCompany)
                        .stage(AutomationStage.AI_ANALYSIS_COMPLETED)
                        .status(AutomationEventStatus.SUCCESS)
                        .message("Gemini detectó " + toolsCount + (toolsCount == 1 ? " acción" : " acciones"))
                        .aiSummary(analysis.summary())
                        .metadata(Map.of("toolsDetected", toolsCount))
                        .build());
            } catch (RuntimeException ex) {
                log.warn("Falló el análisis del correo {} ('{}'): {}", messageId, email.getSubject(), ex.getMessage());
                long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;
                String safeError = sanitizeError(ex.getMessage());

                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(messageId)
                        .stage(AutomationStage.AI_ANALYSIS_COMPLETED)
                        .status(AutomationEventStatus.FAILED)
                        .message("Falló el análisis con Gemini")
                        .error(safeError)
                        .build());

                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(messageId)
                        .subject(email.getSubject())
                        .from(email.getFromAddress())
                        .stage(AutomationStage.PROCESS_FAILED)
                        .status(AutomationEventStatus.FAILED)
                        .message("Procesamiento fallido en análisis de IA")
                        .elapsedMs(elapsedMs)
                        .error(safeError)
                        .metadata(Map.of("finalStatus", "FAILED"))
                        .build());

                return store.failAttempt(email.getId(), "Análisis con Gemini: " + ex.getMessage());
            }
        }

        List<EmailAction> actions = store.actions(email.getId());
        for (EmailAction action : actions) {
            if (action.getStatus() == EmailActionStatus.SKIPPED && email.getAnalyzedAt() == null) {
                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(messageId)
                        .stage(AutomationStage.TOOL_SKIPPED)
                        .status(AutomationEventStatus.SKIPPED)
                        .toolName(action.getToolName())
                        .message(action.getErrorMessage() != null ? action.getErrorMessage() : "Herramienta omitida")
                        .build());
            } else if (EXECUTABLE.contains(action.getStatus())) {
                executeAction(action, email.getId(), messageId, principalName);
            }
        }

        ProcessedEmail finished = store.finishAttempt(email.getId());
        log.info("Correo {} ('{}') → {}", messageId, email.getSubject(), finished.getStatus());

        if (finished.getStatus() == ProcessedEmailStatus.PROCESSED) {
            prepareReply(finished, received, principalName);
        }

        if (finished.getStatus() == ProcessedEmailStatus.PROCESSED || finished.getStatus() == ProcessedEmailStatus.IGNORED) {
            markRead(finished, principalName);
        }

        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;
        publishFinalResult(finished, elapsedMs);

        return finished;
    }

    /** Quita UNREAD en Gmail. Si falla, el correo sigue PROCESSED/IGNORED y solo se reintenta este paso. */
    public void markRead(ProcessedEmail email, String principalName) {
        publisher.publish(AutomationEvent.builder()
                .processedEmailId(email.getId())
                .gmailMessageId(email.getGmailMessageId())
                .stage(AutomationStage.MARK_READ_STARTED)
                .status(AutomationEventStatus.PROCESSING)
                .message("Marcando correo como leído")
                .build());

        try {
            gmailService.markAsRead(gmailToken(principalName), email.getGmailMessageId());
            store.markGmailRead(email.getId());
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(email.getGmailMessageId())
                    .stage(AutomationStage.MARK_READ_COMPLETED)
                    .status(AutomationEventStatus.SUCCESS)
                    .message("Correo marcado como leído")
                    .build());
        } catch (RuntimeException ex) {
            log.warn("No se pudo marcar como leído el correo {}: {}", email.getGmailMessageId(), ex.getMessage());
            store.markGmailReadFailed(email.getId(), ex.getMessage(),
                    OffsetDateTime.now(ZoneOffset.UTC).plus(properties.processing().markReadRetryDelay()));
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(email.getGmailMessageId())
                    .stage(AutomationStage.MARK_READ_FAILED)
                    .status(AutomationEventStatus.FAILED)
                    .message("No se pudo marcar el correo como leído")
                    .error(sanitizeError(ex.getMessage()))
                    .build());
        }
    }

    /**
     * Borrador de respuesta con IA, solo cuando todas las acciones terminaron bien (así puede citar el ticket y la
     * reunión). Nunca se envía: queda en DRAFT para que el usuario lo revise. Un fallo aquí no afecta al correo.
     */
    private void prepareReply(ProcessedEmail email, ReceivedEmail received, String principalName) {
        if (!replyService.isEnabled()) {
            return;
        }
        try {
            Optional<String> skipReason = replyService.skipReason(email.getFromAddress());
            if (skipReason.isPresent()) {
                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(email.getGmailMessageId())
                        .stage(AutomationStage.REPLY_DRAFT_SKIPPED)
                        .status(AutomationEventStatus.SKIPPED)
                        .message(skipReason.get())
                        .build());
                return;
            }
            if (replyService.findByProcessedEmailId(email.getId()).filter(r -> r.getStatus() != ReplyStatus.FAILED).isPresent()) {
                return;
            }

            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(email.getGmailMessageId())
                    .stage(AutomationStage.REPLY_DRAFT_STARTED)
                    .status(AutomationEventStatus.PROCESSING)
                    .message("Redactando respuesta sugerida")
                    .build());

            ProcessedEmailDto summary = ProcessedEmailDto.from(email, store.actions(email.getId()));
            EmailReply reply = replyService.createDraft(new ReplyDraftCommand(
                    email.getId(),
                    email.getGmailMessageId(),
                    email.getThreadId(),
                    email.getFromAddress(),
                    email.getSubject(),
                    email.getAiSummary(),
                    ReplyFacts.from(summary, calendarProperties.timeZone()),
                    received != null ? received.message() : null,
                    principalName));

            if (reply.getStatus() == ReplyStatus.FAILED) {
                publisher.publish(AutomationEvent.builder()
                        .processedEmailId(email.getId())
                        .gmailMessageId(email.getGmailMessageId())
                        .stage(AutomationStage.REPLY_DRAFT_FAILED)
                        .status(AutomationEventStatus.FAILED)
                        .message("No se pudo redactar la respuesta sugerida")
                        .error(sanitizeError(reply.getErrorMessage()))
                        .metadata(Map.of("replyId", reply.getId()))
                        .build());
                return;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("replyId", reply.getId());
            metadata.put("status", reply.getStatus().name());
            metadata.put("to", reply.getToAddress());
            metadata.put("subject", reply.getSubject());
            metadata.put("body", reply.getBody());
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(email.getGmailMessageId())
                    .stage(AutomationStage.REPLY_DRAFT_COMPLETED)
                    .status(AutomationEventStatus.SUCCESS)
                    .externalId(String.valueOf(reply.getId()))
                    .message("Respuesta sugerida lista para revisar")
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("No se pudo preparar la respuesta del correo {}: {}", email.getGmailMessageId(), ex.getMessage());
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(email.getId())
                    .gmailMessageId(email.getGmailMessageId())
                    .stage(AutomationStage.REPLY_DRAFT_FAILED)
                    .status(AutomationEventStatus.FAILED)
                    .message("No se pudo redactar la respuesta sugerida")
                    .error(sanitizeError(ex.getMessage()))
                    .build());
        }
    }

    private void executeAction(EmailAction action, Long emailId, String messageId, String principalName) {
        int attempt = store.markActionProcessing(action.getId());
        int maxAttempts = properties.processing().maxRetries();

        String startMessage = buildToolStartMessage(action.getToolName(), attempt, maxAttempts);
        Map<String, Object> startMetadata = new LinkedHashMap<>();
        startMetadata.put("attempt", attempt);
        startMetadata.put("maxAttempts", maxAttempts);

        publisher.publish(AutomationEvent.builder()
                .processedEmailId(emailId)
                .gmailMessageId(messageId)
                .stage(AutomationStage.TOOL_STARTED)
                .status(AutomationEventStatus.PROCESSING)
                .toolName(action.getToolName())
                .message(startMessage)
                .metadata(startMetadata)
                .build());

        try {
            EmailActionExecutor.Result result = actionExecutor.execute(action, attempt, messageId, principalName);
            store.markActionSuccess(action.getId(), result.externalId(), result.response());
            log.info("Acción {} del correo {} → SUCCESS ({})", action.getToolName(), messageId, result.externalId());

            String completedMsg = buildToolCompletedMessage(action.getToolName(), result.externalId());
            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(emailId)
                    .gmailMessageId(messageId)
                    .stage(AutomationStage.TOOL_COMPLETED)
                    .status(AutomationEventStatus.SUCCESS)
                    .toolName(action.getToolName())
                    .externalId(result.externalId())
                    .externalUrl(result.externalUrl())
                    .message(completedMsg)
                    .metadata(result.metadata() != null && !result.metadata().isEmpty() ? result.metadata() : null)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Acción {} del correo {} → FAILED: {}", action.getToolName(), messageId, ex.getMessage());
            store.markActionFailed(action.getId(), ex.getMessage());

            String failedMsg = buildToolFailedMessage(action.getToolName());
            Map<String, Object> errorMetadata = new LinkedHashMap<>();
            errorMetadata.put("attempt", attempt);
            errorMetadata.put("maxAttempts", maxAttempts);

            publisher.publish(AutomationEvent.builder()
                    .processedEmailId(emailId)
                    .gmailMessageId(messageId)
                    .stage(AutomationStage.TOOL_FAILED)
                    .status(AutomationEventStatus.FAILED)
                    .toolName(action.getToolName())
                    .message(failedMsg)
                    .error(sanitizeError(ex.getMessage()))
                    .metadata(errorMetadata)
                    .build());
        }
    }

    private void publishFinalResult(ProcessedEmail finished, long elapsedMs) {
        switch (finished.getStatus()) {
            case PROCESSED -> publisher.publish(AutomationEvent.builder()
                    .processedEmailId(finished.getId())
                    .gmailMessageId(finished.getGmailMessageId())
                    .subject(finished.getSubject())
                    .from(finished.getFromAddress())
                    .stage(AutomationStage.PROCESS_COMPLETED)
                    .status(AutomationEventStatus.SUCCESS)
                    .message("Procesamiento completado correctamente")
                    .elapsedMs(elapsedMs)
                    .metadata(Map.of("finalStatus", "PROCESSED"))
                    .build());
            case IGNORED -> publisher.publish(AutomationEvent.builder()
                    .processedEmailId(finished.getId())
                    .gmailMessageId(finished.getGmailMessageId())
                    .subject(finished.getSubject())
                    .from(finished.getFromAddress())
                    .stage(AutomationStage.PROCESS_IGNORED)
                    .status(AutomationEventStatus.SUCCESS)
                    .message("Correo ignorado (sin acciones requeridas)")
                    .elapsedMs(elapsedMs)
                    .metadata(Map.of("finalStatus", "IGNORED"))
                    .build());
            case PARTIAL -> publisher.publish(AutomationEvent.builder()
                    .processedEmailId(finished.getId())
                    .gmailMessageId(finished.getGmailMessageId())
                    .subject(finished.getSubject())
                    .from(finished.getFromAddress())
                    .stage(AutomationStage.PROCESS_PARTIAL)
                    .status(AutomationEventStatus.FAILED)
                    .message("Procesamiento parcial: algunas acciones fallaron")
                    .elapsedMs(elapsedMs)
                    .metadata(Map.of("finalStatus", "PARTIAL"))
                    .build());
            case FAILED -> publisher.publish(AutomationEvent.builder()
                    .processedEmailId(finished.getId())
                    .gmailMessageId(finished.getGmailMessageId())
                    .subject(finished.getSubject())
                    .from(finished.getFromAddress())
                    .stage(AutomationStage.PROCESS_FAILED)
                    .status(AutomationEventStatus.FAILED)
                    .message("Procesamiento fallido")
                    .elapsedMs(elapsedMs)
                    .error(sanitizeError(finished.getErrorMessage()))
                    .metadata(Map.of("finalStatus", "FAILED"))
                    .build());
            default -> {
            }
        }
    }

    private String buildToolStartMessage(String toolName, int attempt, int maxAttempts) {
        if (attempt > 1) {
            String displayName = switch (toolName) {
                case "crear_ticket_jira" -> "Jira";
                case "agendar_reunion_google_calendar" -> "Google Calendar";
                case "actualizar_contacto_crm" -> "CRM";
                default -> toolName;
            };
            return "Reintentando " + displayName + " (intento " + attempt + " de " + maxAttempts + ")";
        }
        return switch (toolName) {
            case "crear_ticket_jira" -> "Creando ticket en Jira";
            case "agendar_reunion_google_calendar" -> "Creando reunión en Google Calendar";
            case "actualizar_contacto_crm" -> "Actualizando contacto en CRM";
            default -> "Ejecutando " + toolName;
        };
    }

    private String buildToolCompletedMessage(String toolName, String externalId) {
        return switch (toolName) {
            case "crear_ticket_jira" -> "Ticket " + externalId + " creado correctamente";
            case "agendar_reunion_google_calendar" -> "Reunión creada en Google Calendar";
            case "actualizar_contacto_crm" -> "Contacto CRM actualizado correctamente";
            default -> "Acción " + toolName + " completada con éxito";
        };
    }

    private String buildToolFailedMessage(String toolName) {
        return switch (toolName) {
            case "crear_ticket_jira" -> "No se pudo crear ticket en Jira";
            case "agendar_reunion_google_calendar" -> "No se pudo crear evento en Google Calendar";
            default -> "No se pudo completar la acción " + toolName;
        };
    }

    private String extractDetectedCompany(List<RequestedToolCall> toolCalls) {
        if (toolCalls == null) {
            return null;
        }
        for (RequestedToolCall call : toolCalls) {
            if (call.arguments() != null) {
                Object company = call.arguments().get("company");
                if (company == null) {
                    company = call.arguments().get("empresa");
                }
                if (company != null && !company.toString().isBlank()) {
                    return company.toString().strip();
                }
            }
        }
        return null;
    }

    private String sanitizeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Error no especificado";
        }
        String firstLine = raw.lines().findFirst().orElse(raw).strip();
        return firstLine.length() > 250 ? firstLine.substring(0, 250) + "..." : firstLine;
    }

    private String gmailToken(String principalName) {
        return tokenService.getAccessTokenWithScope(principalName, GmailService.GMAIL_MODIFY_SCOPE);
    }

    private static AnalyzeEmailRequest toAnalyzeRequest(GmailMessageDto message) {
        String body = message.body() == null || message.body().isBlank() ? message.snippet() : message.body();
        if (body != null && body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS);
        }
        return new AnalyzeEmailRequest(message.id(), message.threadId(), message.from(), message.subject(),
                message.date(), body == null ? "" : body);
    }
}

