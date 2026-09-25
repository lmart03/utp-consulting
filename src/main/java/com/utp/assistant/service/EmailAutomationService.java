package com.utp.assistant.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.utp.assistant.config.AssistantProperties;
import com.utp.assistant.config.GeminiProperties;
import com.utp.assistant.entity.ProcessedEmail;
import com.utp.assistant.entity.ProcessedEmailStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Un ciclo de automatización: (1) reintentos vencidos según BD, (2) correos nuevos de Gmail.
 * Procesamiento secuencial; un AtomicBoolean impide ciclos solapados (scheduler + process-now) en esta instancia,
 * y la UNIQUE de BD protege cada correo.
 * <p>
 * Cutoff: automationStartedAt se fija una vez al iniciar el backend. Con process-old-emails=false la query de Gmail
 * agrega "after:&lt;epoch&gt;" y además se compara el internalDate real de cada mensaje, así los no leídos anteriores
 * al arranque nunca se procesan.
 */
@Slf4j
@Service
public class EmailAutomationService {

    public record CycleResult(boolean executed, String message, int newEmails, int retried, int skipped, int errors,
                              OffsetDateTime finishedAt) {

        static CycleResult notExecuted(String message) {
            return new CycleResult(false, message, 0, 0, 0, 0, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private static final int MAX_DUE_PER_CYCLE = 20;

    private final GmailService gmailService;
    private final GoogleTokenService tokenService;
    private final GoogleAutomationAccount automationAccount;
    private final ProcessedEmailStore store;
    private final EmailProcessor processor;
    private final AssistantProperties properties;
    private final GeminiProperties geminiProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Instant automationStartedAt = Instant.now();
    private volatile CycleResult lastResult;
    private volatile String lastWarning;

    public EmailAutomationService(GmailService gmailService, GoogleTokenService tokenService,
                                  GoogleAutomationAccount automationAccount, ProcessedEmailStore store,
                                  EmailProcessor processor, AssistantProperties properties,
                                  GeminiProperties geminiProperties) {
        this.gmailService = gmailService;
        this.tokenService = tokenService;
        this.automationAccount = automationAccount;
        this.store = store;
        this.processor = processor;
        this.properties = properties;
        this.geminiProperties = geminiProperties;
        log.info("Automatización iniciada. Cutoff de correos: {} (process-old-emails={})",
                automationStartedAt, properties.automation().processOldEmails());
    }

    /** Ejecuta un ciclo si no hay otro en curso. Nunca lanza excepciones (no debe detener al scheduler). */
    public CycleResult runCycle() {
        if (!running.compareAndSet(false, true)) {
            return CycleResult.notExecuted("Ya hay un ciclo de automatización en ejecución.");
        }
        try {
            lastResult = doRunCycle();
        } catch (RuntimeException ex) {
            log.error("Error inesperado en el ciclo de automatización", ex);
            lastResult = CycleResult.notExecuted("Error inesperado: " + ex.getMessage());
        } finally {
            running.set(false);
        }
        return lastResult;
    }

    private CycleResult doRunCycle() {
        Optional<GoogleAutomationAccount.Account> account = automationAccount.current();
        if (account.isEmpty()) {
            return warn("Sin cuenta Google: inicia sesión en /oauth2/authorization/google para activar la automatización.");
        }
        if (!geminiProperties.isConfigured()) {
            return warn("GEMINI_API_KEY no está configurada.");
        }
        String principal = account.get().principalName();
        String gmailToken;
        try {
            gmailToken = tokenService.getAccessTokenWithScope(principal, GmailService.GMAIL_MODIFY_SCOPE);
        } catch (RuntimeException ex) {
            return warn(ex.getMessage());
        }
        lastWarning = null;

        int retried = 0;
        int newEmails = 0;
        int skipped = 0;
        int errors = 0;

        // 1) Reintentos vencidos (PARTIAL/FAILED/lease PROCESSING vencido) y marcados como leído pendientes.
        for (ProcessedEmail due : store.findDue(MAX_DUE_PER_CYCLE)) {
            try {
                if (handleDue(due, principal)) {
                    retried++;
                }
            } catch (RuntimeException ex) {
                errors++;
                log.error("Error reintentando el correo {} ('{}')", due.getGmailMessageId(), due.getSubject(), ex);
            }
        }

        // 2) Correos nuevos. UNREAD solo sirve para encontrar candidatos; la idempotencia la da la BD.
        List<String> candidates = gmailService.listMessageIds(gmailToken, gmailQuery(), properties.automation().maxResults());
        for (String messageId : candidates) {
            try {
                if (store.isRegistered(messageId)) {
                    skipped++;
                    continue;
                }
                ReceivedEmail received = gmailService.getReceivedEmail(gmailToken, messageId);
                if (!isAfterCutoff(received)) {
                    skipped++;
                    continue;
                }
                Optional<ProcessedEmail> claimed = store.tryClaim(received, leaseUntil());
                if (claimed.isEmpty()) {
                    skipped++;
                    continue;
                }
                newEmails++;
                processor.process(claimed.get(), received, principal);
            } catch (RuntimeException ex) {
                errors++;
                log.error("Error procesando el correo {}", messageId, ex);
            }
        }

        if (newEmails + retried + errors > 0) {
            log.info("Ciclo de automatización: nuevos={}, reintentos={}, omitidos={}, errores={}", newEmails, retried, skipped, errors);
        }
        return new CycleResult(true, "Ciclo completado.", newEmails, retried, skipped, errors, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private boolean handleDue(ProcessedEmail due, String principal) {
        if (due.getStatus() == ProcessedEmailStatus.PROCESSED || due.getStatus() == ProcessedEmailStatus.IGNORED) {
            if (!due.isGmailMarkedRead()) {
                processor.markRead(due, principal); // nunca se vuelven a ejecutar las herramientas
                return true;
            }
            return false;
        }
        Optional<ProcessedEmail> reclaimed = store.reclaim(due, leaseUntil());
        reclaimed.ifPresent(email -> processor.process(email, null, principal));
        return reclaimed.isPresent();
    }

    boolean isAfterCutoff(ReceivedEmail received) {
        if (properties.automation().processOldEmails()) {
            return true;
        }
        return received.receivedAt() != null && !received.receivedAt().isBefore(automationStartedAt);
    }

    String gmailQuery() {
        String query = properties.automation().gmailQuery();
        return properties.automation().processOldEmails() ? query : query + " after:" + automationStartedAt.getEpochSecond();
    }

    private OffsetDateTime leaseUntil() {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(properties.automation().processingLease());
    }

    private CycleResult warn(String message) {
        if (!message.equals(lastWarning)) {
            log.warn("Automatización en espera: {}", message);
            lastWarning = message;
        }
        return CycleResult.notExecuted(message);
    }

    public Instant automationStartedAt() {
        return automationStartedAt;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<CycleResult> lastResult() {
        return Optional.ofNullable(lastResult);
    }
}
