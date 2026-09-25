package com.utp.assistant.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

import com.utp.assistant.config.AssistantProperties;
import com.utp.assistant.dto.AnalyzeEmailRequest;
import com.utp.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.dto.GmailMessageDto;
import com.utp.assistant.entity.EmailAction;
import com.utp.assistant.entity.EmailActionStatus;
import com.utp.assistant.entity.ProcessedEmail;
import com.utp.assistant.entity.ProcessedEmailStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Procesa un correo ya reclamado: Gemini → registrar acciones → ejecutar solo las pendientes/fallidas →
 * estado final → marcar leído en Gmail solo si todo terminó bien. Cada paso persiste en una transacción corta.
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

    /**
     * @param received contenido del correo si ya se leyó en este ciclo; null en reintentos (se vuelve a leer de
     *                 Gmail solo si Gemini aún no lo había analizado, ya que el cuerpo no se guarda en BD).
     */
    public ProcessedEmail process(ProcessedEmail email, ReceivedEmail received, String principalName) {
        String messageId = email.getGmailMessageId();
        if (email.getAnalyzedAt() == null) {
            try {
                ReceivedEmail content = received != null ? received
                        : gmailService.getReceivedEmail(gmailToken(principalName), messageId);
                GeminiEmailAnalysisResponse analysis = geminiService.analyzeEmail(toAnalyzeRequest(content.message()));
                store.recordAnalysis(email.getId(), analysis.summary(), analysis.toolCalls(), analysis.rejectedToolCalls());
            } catch (RuntimeException ex) {
                log.warn("Falló el análisis del correo {} ('{}'): {}", messageId, email.getSubject(), ex.getMessage());
                return store.failAttempt(email.getId(), "Análisis con Gemini: " + ex.getMessage());
            }
        }

        for (EmailAction action : store.actions(email.getId())) {
            if (EXECUTABLE.contains(action.getStatus())) {
                executeAction(action, messageId, principalName);
            }
        }

        ProcessedEmail finished = store.finishAttempt(email.getId());
        log.info("Correo {} ('{}') → {}", messageId, email.getSubject(), finished.getStatus());
        if (finished.getStatus() == ProcessedEmailStatus.PROCESSED || finished.getStatus() == ProcessedEmailStatus.IGNORED) {
            markRead(finished, principalName);
        }
        return finished;
    }

    /** Quita UNREAD en Gmail. Si falla, el correo sigue PROCESSED/IGNORED y solo se reintenta este paso. */
    public void markRead(ProcessedEmail email, String principalName) {
        try {
            gmailService.markAsRead(gmailToken(principalName), email.getGmailMessageId());
            store.markGmailRead(email.getId());
        } catch (RuntimeException ex) {
            log.warn("No se pudo marcar como leído el correo {}: {}", email.getGmailMessageId(), ex.getMessage());
            store.markGmailReadFailed(email.getId(), ex.getMessage(),
                    OffsetDateTime.now(ZoneOffset.UTC).plus(properties.processing().markReadRetryDelay()));
        }
    }

    private void executeAction(EmailAction action, String messageId, String principalName) {
        int attempt = store.markActionProcessing(action.getId());
        try {
            EmailActionExecutor.Result result = actionExecutor.execute(action, attempt, messageId, principalName);
            store.markActionSuccess(action.getId(), result.externalId(), result.response());
            log.info("Acción {} del correo {} → SUCCESS ({})", action.getToolName(), messageId, result.externalId());
        } catch (RuntimeException ex) {
            log.warn("Acción {} del correo {} → FAILED: {}", action.getToolName(), messageId, ex.getMessage());
            store.markActionFailed(action.getId(), ex.getMessage());
        }
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
