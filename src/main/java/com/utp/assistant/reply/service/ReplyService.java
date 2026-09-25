package com.utp.assistant.reply.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.utp.assistant.assistant.dto.ReplyDraftRequest;
import com.utp.assistant.assistant.service.GeminiService;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.service.GmailService;
import com.utp.assistant.reply.config.ReplyProperties;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import com.utp.assistant.reply.exception.InvalidReplyStateException;
import com.utp.assistant.reply.exception.ReplyNotFoundException;
import com.utp.assistant.reply.repository.EmailReplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Borradores de respuesta con IA. La automatización solo genera el borrador; el envío siempre es una acción
 * explícita del usuario (send) y usa su propia sesión de Google.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyService {

    /** Remitentes automáticos: responderles no sirve y podría generar bucles con autorespuestas. */
    private static final Pattern AUTOMATED_SENDER = Pattern.compile(
            "no-?reply|do-?not-?reply|mailer-daemon|postmaster|^bounces?\\b|^notifications?\\b|^newsletter",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_BODY = 20_000;
    private static final int MAX_ERROR = 1000;

    private final EmailReplyRepository repository;
    private final GeminiService geminiService;
    private final GmailService gmailService;
    private final GoogleTokenService tokenService;
    private final ReplyProperties properties;

    public boolean isEnabled() {
        return properties.enabled();
    }

    /** Motivo para no sugerir respuesta; vacío si corresponde generar el borrador. */
    public Optional<String> skipReason(String from) {
        String address = recipient(from);
        if (address == null || !address.contains("@")) {
            return Optional.of("El remitente no tiene una dirección válida.");
        }
        if (AUTOMATED_SENDER.matcher(address.substring(0, address.indexOf('@'))).find()) {
            return Optional.of("Remitente automático: no requiere respuesta.");
        }
        return Optional.empty();
    }

    public EmailReply get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ReplyNotFoundException("No existe la respuesta " + id + "."));
    }

    public Optional<EmailReply> findByProcessedEmailId(Long processedEmailId) {
        return repository.findByProcessedEmailId(processedEmailId);
    }

    public Map<Long, EmailReply> findByProcessedEmailIds(Collection<Long> processedEmailIds) {
        if (processedEmailIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByProcessedEmailIdIn(processedEmailIds).stream()
                .collect(Collectors.toMap(EmailReply::getProcessedEmailId, Function.identity()));
    }

    /**
     * Genera y guarda el borrador. Idempotente: si el correo ya tiene uno (salvo FAILED) lo devuelve sin llamar a
     * Gemini. No lanza excepciones por Gemini: si falla, el borrador queda FAILED con el motivo.
     */
    public EmailReply createDraft(ReplyDraftCommand command) {
        Optional<EmailReply> existing = repository.findByProcessedEmailId(command.processedEmailId());
        if (existing.isPresent() && existing.get().getStatus() != ReplyStatus.FAILED) {
            return existing.get();
        }

        EmailReply reply = existing.orElseGet(EmailReply::new);
        reply.setProcessedEmailId(command.processedEmailId());
        reply.setGmailMessageId(command.gmailMessageId());
        reply.setThreadId(command.threadId());
        reply.setToAddress(recipient(command.from()));
        reply.setSubject(truncate(replySubject(command.subject()), 1000));
        reply.setContextSummary(command.summary());
        reply.setContextFacts(String.join("\n", command.facts()));

        GmailMessageDto message = command.message() != null ? command.message()
                : readOriginal(command.principalName(), command.gmailMessageId());
        generate(reply, message, command.from());

        try {
            return repository.save(reply);
        } catch (DataIntegrityViolationException alreadyCreated) {
            // Otro proceso guardó el borrador del mismo correo primero: se usa ese.
            return repository.findByProcessedEmailId(command.processedEmailId()).orElseThrow(() -> alreadyCreated);
        }
    }

    /** Vuelve a redactar un borrador (fallido o que no convenció). Lee el correo original con la sesión del usuario. */
    public EmailReply regenerate(Long id, Authentication authentication) {
        EmailReply reply = get(id);
        if (reply.getStatus() != ReplyStatus.DRAFT && reply.getStatus() != ReplyStatus.FAILED) {
            throw new InvalidReplyStateException(stateMessage(reply.getStatus()));
        }
        GmailMessageDto message = gmailService.getMessage(authentication, reply.getGmailMessageId());
        generate(reply, message, message.from());
        return repository.save(reply);
    }

    /**
     * Envía el texto aprobado (tal cual o editado) en el hilo del correo original. DRAFT → SENDING es atómico,
     * así que un doble clic no envía dos veces. Si Gmail falla vuelve a DRAFT conservando el texto editado.
     */
    public EmailReply send(Long id, String body, Authentication authentication) {
        EmailReply reply = get(id);
        if (reply.getStatus() != ReplyStatus.DRAFT) {
            throw new InvalidReplyStateException(stateMessage(reply.getStatus()));
        }
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_BODY) {
            throw new IllegalArgumentException("El texto de la respuesta está vacío o es demasiado largo.");
        }
        // El token se obtiene antes de bloquear: si la sesión expiró, el borrador sigue en DRAFT.
        String accessToken = tokenService.getAccessTokenWithScope(authentication, GmailService.GMAIL_MODIFY_SCOPE);
        if (repository.claimForSending(id, now(), ReplyStatus.DRAFT, ReplyStatus.SENDING) != 1) {
            throw new InvalidReplyStateException("La respuesta ya se está enviando o fue enviada.");
        }

        try {
            String sentId = gmailService.sendReply(accessToken, reply.getGmailMessageId(), reply.getThreadId(),
                    reply.getToAddress(), reply.getSubject(), text);
            EmailReply sent = get(id);
            sent.setBody(text);
            sent.setStatus(ReplyStatus.SENT);
            sent.setSentGmailMessageId(sentId);
            sent.setSentAt(now());
            sent.setErrorMessage(null);
            log.info("Respuesta {} del correo {} enviada a {}", id, reply.getGmailMessageId(), reply.getToAddress());
            return repository.save(sent);
        } catch (RuntimeException ex) {
            EmailReply draft = get(id);
            draft.setBody(text);
            draft.setStatus(ReplyStatus.DRAFT);
            draft.setErrorMessage(truncate("No se pudo enviar: " + ex.getMessage(), MAX_ERROR));
            repository.save(draft);
            throw ex;
        }
    }

    public EmailReply discard(Long id) {
        EmailReply reply = get(id);
        if (reply.getStatus() != ReplyStatus.DRAFT && reply.getStatus() != ReplyStatus.FAILED) {
            throw new InvalidReplyStateException(stateMessage(reply.getStatus()));
        }
        reply.setStatus(ReplyStatus.DISCARDED);
        return repository.save(reply);
    }

    private void generate(EmailReply reply, GmailMessageDto message, String from) {
        List<String> facts = reply.getContextFacts() == null ? List.of()
                : Arrays.stream(reply.getContextFacts().split("\n")).filter(f -> !f.isBlank()).toList();
        String body = message == null ? null
                : message.body() == null || message.body().isBlank() ? message.snippet() : message.body();
        try {
            String draft = geminiService.draftReply(new ReplyDraftRequest(
                    message != null && message.from() != null ? message.from() : from,
                    message != null ? message.subject() : reply.getSubject(),
                    message != null ? message.date() : null,
                    body,
                    reply.getContextSummary(),
                    facts,
                    properties.signature()));
            if (draft == null || draft.isBlank()) {
                throw new IllegalStateException("Gemini no devolvió texto para el borrador.");
            }
            String text = truncate(draft.strip(), MAX_BODY);
            reply.setBody(text);
            reply.setAiBody(text);
            reply.setStatus(ReplyStatus.DRAFT);
            reply.setErrorMessage(null);
        } catch (RuntimeException ex) {
            log.warn("No se pudo generar el borrador de respuesta del correo {}: {}", reply.getGmailMessageId(), ex.getMessage());
            reply.setStatus(ReplyStatus.FAILED);
            reply.setErrorMessage(truncate(ex.getMessage(), MAX_ERROR));
        }
    }

    /** El cuerpo no se guarda en BD: en reintentos se vuelve a leer de Gmail. Si falla, se redacta con el resumen. */
    private GmailMessageDto readOriginal(String principalName, String gmailMessageId) {
        try {
            String token = tokenService.getAccessTokenWithScope(principalName, GmailService.GMAIL_MODIFY_SCOPE);
            return gmailService.getReceivedEmail(token, gmailMessageId).message();
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer el correo {} para redactar la respuesta: {}", gmailMessageId, ex.getMessage());
            return null;
        }
    }

    private static String stateMessage(ReplyStatus status) {
        return switch (status) {
            case SENT -> "La respuesta ya fue enviada.";
            case SENDING -> "La respuesta se está enviando.";
            case DISCARDED -> "La respuesta fue descartada.";
            case FAILED -> "No hay borrador para enviar: regénéralo primero.";
            case DRAFT -> "El borrador está pendiente de revisión.";
        };
    }

    /** "Ana Torres &lt;Ana@TechCorp.com&gt;" → "ana@techcorp.com" */
    static String recipient(String from) {
        if (from == null) {
            return null;
        }
        int start = from.lastIndexOf('<');
        int end = from.lastIndexOf('>');
        String address = (start >= 0 && end > start ? from.substring(start + 1, end) : from)
                .replaceAll("[\\r\\n\\s]+", "")
                .toLowerCase(Locale.ROOT);
        return address.isEmpty() ? null : address;
    }

    static String replySubject(String subject) {
        String clean = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").strip();
        if (clean.isEmpty()) {
            return "Re: (sin asunto)";
        }
        return clean.toLowerCase(Locale.ROOT).startsWith("re:") ? clean : "Re: " + clean;
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
