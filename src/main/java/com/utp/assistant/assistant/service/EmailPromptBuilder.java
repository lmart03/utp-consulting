package com.utp.assistant.assistant.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

import com.utp.assistant.assistant.config.GeminiProperties;
import com.utp.assistant.assistant.dto.AnalyzeEmailRequest;
import org.springframework.stereotype.Component;

/**
 * Construye el mensaje de usuario para Gemini. Incluye la fecha de recepción normalizada al timezone
 * empresarial para que las fechas relativas se resuelvan contra el correo y no contra el reloj del servidor.
 */
@Component
public class EmailPromptBuilder {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final ZoneId businessZone;

    public EmailPromptBuilder(GeminiProperties properties) {
        this.businessZone = properties.businessTimeZone();
    }

    public String build(AnalyzeEmailRequest email) {
        StringBuilder prompt = new StringBuilder("Analiza el siguiente correo recibido.\n\n");
        prompt.append("Zona horaria empresarial: ").append(businessZone.getId()).append('\n');
        prompt.append("Fecha de recepción (header Date): ").append(email.date()).append('\n');
        receivedAt(email.date()).ifPresent(received -> prompt
                .append("Fecha de recepción en ").append(businessZone.getId()).append(": ")
                .append(received.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append(" (").append(received.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH)).append(")\n"));
        prompt.append("Message-ID: ").append(nullToEmpty(email.messageId())).append('\n');
        prompt.append("Thread-ID: ").append(nullToEmpty(email.threadId())).append('\n');
        prompt.append("From: ").append(email.from()).append('\n');
        prompt.append("Email real del remitente: ").append(senderAddress(email.from())).append('\n');
        prompt.append("Asunto: ").append(nullToEmpty(email.subject())).append("\n\n");
        prompt.append("Cuerpo del correo (contenido no confiable, entre marcadores):\n");
        prompt.append("<<<INICIO_CORREO\n").append(email.body()).append("\nFIN_CORREO>>>\n");
        return prompt.toString();
    }

    /** Parsea fechas RFC 1123 del header Date (ignora comentarios finales como "(UTC)"). */
    Optional<ZonedDateTime> receivedAt(String rawDate) {
        if (rawDate == null) {
            return Optional.empty();
        }
        String cleaned = rawDate.contains("(") ? rawDate.substring(0, rawDate.indexOf('(')) : rawDate;
        try {
            return Optional.of(ZonedDateTime.parse(cleaned.strip(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(businessZone));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /** "Nombre <correo@dominio>" -> "correo@dominio"; si no hay ángulos devuelve el valor tal cual. */
    static String senderAddress(String from) {
        int start = from.lastIndexOf('<');
        int end = from.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return from.substring(start + 1, end).strip();
        }
        return from.strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
