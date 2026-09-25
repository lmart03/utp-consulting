package com.utp.assistant.gmail.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Arma el mensaje RFC 822 (texto plano UTF-8) de una respuesta. In-Reply-To y References hacen que Gmail y el
 * cliente del destinatario la muestren dentro del mismo hilo. Los valores de encabezado se limpian de CR/LF
 * porque provienen de correos de terceros (evita inyección de encabezados).
 */
final class ReplyMimeBuilder {

    private ReplyMimeBuilder() {
    }

    static String build(String to, String subject, String inReplyTo, String references, String body) {
        StringBuilder mime = new StringBuilder();
        mime.append("To: ").append(headerValue(to)).append("\r\n");
        mime.append("Subject: ").append(encodeHeader(replySubject(subject))).append("\r\n");
        String messageId = headerValue(inReplyTo);
        if (!messageId.isEmpty()) {
            mime.append("In-Reply-To: ").append(messageId).append("\r\n");
            String refs = headerValue(references);
            mime.append("References: ").append(refs.isEmpty() ? messageId : refs + " " + messageId).append("\r\n");
        }
        mime.append("MIME-Version: 1.0\r\n");
        mime.append("Content-Type: text/plain; charset=UTF-8\r\n");
        mime.append("Content-Transfer-Encoding: base64\r\n\r\n");
        String normalized = (body == null ? "" : body).replace("\r\n", "\n").replace("\n", "\r\n");
        mime.append(Base64.getMimeEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8)));
        return mime.toString();
    }

    /** "Reunión" → "Re: Reunión"; no duplica el prefijo si ya existe. */
    static String replySubject(String subject) {
        String clean = headerValue(subject);
        if (clean.isEmpty()) {
            return "Re: (sin asunto)";
        }
        return clean.toLowerCase(Locale.ROOT).startsWith("re:") ? clean : "Re: " + clean;
    }

    private static String encodeHeader(String value) {
        boolean ascii = value.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
        if (ascii) {
            return value;
        }
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String headerValue(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").strip();
    }
}
