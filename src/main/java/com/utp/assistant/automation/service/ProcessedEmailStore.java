package com.utp.assistant.automation.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.utp.assistant.assistant.dto.RequestedToolCall;
import com.utp.assistant.assistant.service.AssistantTool;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.EmailActionStatus;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.automation.entity.ProcessedEmailStatus;
import com.utp.assistant.automation.repository.EmailActionRepository;
import com.utp.assistant.automation.repository.ProcessedEmailRepository;
import com.utp.assistant.gmail.service.ReceivedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistencia de la automatización con transacciones cortas: ninguna transacción queda abierta durante
 * llamadas a Gmail, Gemini, Jira o Calendar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedEmailStore {

    static final String TOOL_NOT_ALLOWED = "Herramienta fuera de la allowlist: no se ejecuta.";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_TEXT = 4000;

    private final ProcessedEmailRepository emailRepository;
    private final EmailActionRepository actionRepository;
    private final RetryPolicy retryPolicy;

    public boolean isRegistered(String gmailMessageId) {
        return emailRepository.existsByGmailMessageId(gmailMessageId);
    }

    /**
     * Claim de un correo nuevo: INSERT con status PROCESSING. La UNIQUE(gmail_message_id) garantiza que solo un
     * worker gana; si otro ya lo tomó se devuelve vacío (caso esperado, no es un error).
     * Sin @Transactional a propósito: saveAndFlush usa su propia transacción corta.
     */
    public Optional<ProcessedEmail> tryClaim(ReceivedEmail received, OffsetDateTime leaseUntil) {
        ProcessedEmail email = new ProcessedEmail();
        email.setGmailMessageId(received.message().id());
        email.setThreadId(received.message().threadId());
        email.setFromAddress(truncate(received.message().from(), 512));
        email.setSubject(truncate(received.message().subject(), 1000));
        email.setReceivedAt(received.receivedAt() == null ? null : received.receivedAt().atOffset(ZoneOffset.UTC));
        email.setStatus(ProcessedEmailStatus.PROCESSING);
        email.setAttemptCount(1);
        email.setNextRetryAt(leaseUntil);
        try {
            return Optional.of(emailRepository.saveAndFlush(email));
        } catch (DataIntegrityViolationException alreadyClaimed) {
            log.debug("El correo {} ya fue reclamado por otro proceso; se omite", received.message().id());
            return Optional.empty();
        }
    }

    /** Re-claim atómico de un correo vencido (PARTIAL/FAILED con reintento o PROCESSING con lease vencido). */
    @Transactional
    public Optional<ProcessedEmail> reclaim(ProcessedEmail due, OffsetDateTime leaseUntil) {
        int updated = emailRepository.reclaim(due.getId(), due.getStatus(), now(), leaseUntil);
        return updated == 1 ? emailRepository.findById(due.getId()) : Optional.empty();
    }

    /** Guarda el resumen y registra una EmailAction por herramienta (PENDING, o SKIPPED si está fuera de la allowlist). */
    @Transactional
    public void recordAnalysis(Long emailId, String summary, List<RequestedToolCall> toolCalls, List<String> rejectedTools) {
        ProcessedEmail email = emailRepository.findById(emailId).orElseThrow();
        email.setAiSummary(summary == null || summary.isBlank() ? null : summary);
        email.setAnalyzedAt(now());
        email.setErrorMessage(null);

        Set<String> registered = new HashSet<>();
        for (RequestedToolCall call : toolCalls) {
            Optional<AssistantTool> tool = AssistantTool.fromFunctionName(call.name());
            String payload = toJson(call.arguments());
            if (tool.isEmpty()) {
                addAction(email, call.name(), EmailActionStatus.SKIPPED, payload, TOOL_NOT_ALLOWED, registered);
            } else {
                addAction(email, call.name(), EmailActionStatus.PENDING, payload, null, registered);
            }
        }
        for (String rejected : rejectedTools) {
            addAction(email, rejected, EmailActionStatus.SKIPPED, null, TOOL_NOT_ALLOWED, registered);
        }
    }

    public List<EmailAction> actions(Long emailId) {
        return actionRepository.findByProcessedEmailIdOrderByIdAsc(emailId);
    }

    /** PENDING/FAILED → PROCESSING. Devuelve el número de intento de esta acción (1 = primera vez). */
    @Transactional
    public int markActionProcessing(Long actionId) {
        EmailAction action = actionRepository.findById(actionId).orElseThrow();
        action.setStatus(EmailActionStatus.PROCESSING);
        action.setAttemptCount(action.getAttemptCount() + 1);
        return action.getAttemptCount();
    }

    @Transactional
    public void markActionSuccess(Long actionId, String externalId, Object response) {
        EmailAction action = actionRepository.findById(actionId).orElseThrow();
        action.setStatus(EmailActionStatus.SUCCESS);
        action.setExternalId(externalId);
        action.setResponsePayload(toJson(response));
        action.setErrorMessage(null);
    }

    @Transactional
    public void markActionFailed(Long actionId, String error) {
        EmailAction action = actionRepository.findById(actionId).orElseThrow();
        action.setStatus(EmailActionStatus.FAILED);
        action.setErrorMessage(truncate(error, MAX_TEXT));
    }

    /**
     * Cierra el intento según las acciones: sin acciones → IGNORED; todas SUCCESS/SKIPPED → PROCESSED;
     * alguna fallida → PARTIAL con reintento, o FAILED si se agotaron los reintentos.
     */
    @Transactional
    public ProcessedEmail finishAttempt(Long emailId) {
        ProcessedEmail email = emailRepository.findById(emailId).orElseThrow();
        List<EmailAction> actions = actionRepository.findByProcessedEmailIdOrderByIdAsc(emailId);
        OffsetDateTime now = now();

        List<EmailAction> failed = actions.stream()
                .filter(a -> a.getStatus() != EmailActionStatus.SUCCESS && a.getStatus() != EmailActionStatus.SKIPPED)
                .toList();
        if (actions.isEmpty() || failed.isEmpty()) {
            email.setStatus(actions.isEmpty() ? ProcessedEmailStatus.IGNORED : ProcessedEmailStatus.PROCESSED);
            email.setProcessedAt(now);
            email.setNextRetryAt(null);
            email.setErrorMessage(null);
            return email;
        }

        String error = "Acciones fallidas: " + String.join(", ", failed.stream()
                .map(a -> a.getToolName() + " (" + a.getErrorMessage() + ")").toList());
        scheduleRetryOrFail(email, ProcessedEmailStatus.PARTIAL, error, now);
        return email;
    }

    /** Fallo antes de ejecutar herramientas (Gemini, lectura de Gmail): FAILED con reintento o definitivo. */
    @Transactional
    public ProcessedEmail failAttempt(Long emailId, String error) {
        ProcessedEmail email = emailRepository.findById(emailId).orElseThrow();
        scheduleRetryOrFail(email, ProcessedEmailStatus.FAILED, error, now());
        return email;
    }

    @Transactional
    public void markGmailRead(Long emailId) {
        ProcessedEmail email = emailRepository.findById(emailId).orElseThrow();
        email.setGmailMarkedRead(true);
        email.setNextRetryAt(null);
        email.setErrorMessage(null);
    }

    /** El estado sigue PROCESSED/IGNORED: solo se reintentará quitar UNREAD, nunca las herramientas. */
    @Transactional
    public void markGmailReadFailed(Long emailId, String error, OffsetDateTime retryAt) {
        ProcessedEmail email = emailRepository.findById(emailId).orElseThrow();
        email.setErrorMessage(truncate("No se pudo marcar como leído en Gmail: " + error, MAX_TEXT));
        email.setNextRetryAt(retryAt);
    }

    public List<ProcessedEmail> findDue(int limit) {
        return emailRepository.findDue(now(), List.of(ProcessedEmailStatus.values()), PageRequest.of(0, limit));
    }

    public List<ProcessedEmail> latest(int limit) {
        return emailRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public Optional<ProcessedEmail> findById(Long id) {
        return emailRepository.findById(id);
    }

    public Map<ProcessedEmailStatus, Long> countByStatus() {
        Map<ProcessedEmailStatus, Long> counts = new java.util.EnumMap<>(ProcessedEmailStatus.class);
        for (ProcessedEmailStatus status : ProcessedEmailStatus.values()) {
            counts.put(status, emailRepository.countByStatus(status));
        }
        return counts;
    }

    private void scheduleRetryOrFail(ProcessedEmail email, ProcessedEmailStatus retryStatus, String error, OffsetDateTime now) {
        Optional<OffsetDateTime> retryAt = retryPolicy.nextRetryAt(email.getAttemptCount(), now);
        email.setStatus(retryAt.isPresent() ? retryStatus : ProcessedEmailStatus.FAILED);
        email.setNextRetryAt(retryAt.orElse(null));
        email.setErrorMessage(truncate(retryAt.isPresent() ? error
                : error + " | Máximo de reintentos alcanzado (" + retryPolicy.maxRetries() + ").", MAX_TEXT));
    }

    private void addAction(ProcessedEmail email, String toolName, EmailActionStatus status, String requestPayload,
                           String detail, Set<String> registered) {
        String name = truncate(toolName == null || toolName.isBlank() ? "(sin nombre)" : toolName, 100);
        // Una sola acción por herramienta y correo (además de la UNIQUE en BD).
        if (!registered.add(name) || actionRepository.existsByProcessedEmailIdAndToolName(email.getId(), name)) {
            log.warn("Gemini repitió la herramienta {} para el correo {}; se registra una sola vez", name, email.getGmailMessageId());
            return;
        }
        EmailAction action = new EmailAction();
        action.setProcessedEmail(email);
        action.setToolName(name);
        action.setStatus(status);
        action.setRequestPayload(requestPayload);
        action.setErrorMessage(detail);
        actionRepository.save(action);
    }

    static String toJson(Object value) {
        return value == null ? null : JSON.writeValueAsString(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
