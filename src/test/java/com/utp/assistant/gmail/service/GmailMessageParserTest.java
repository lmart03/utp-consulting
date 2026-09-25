package com.utp.assistant.gmail.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.exception.MimeDecodingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailMessageParserTest {

    private final GmailMessageParser parser = new GmailMessageParser();

    @Test
    void extractsTextPlainFromPayloadBody() {
        MessagePart payload = leaf("text/plain", "Hola equipo, estamos interesados.");

        assertThat(parser.extractBody(payload)).isEqualTo("Hola equipo, estamos interesados.");
    }

    @Test
    void prefersTextPlainInMultipartAlternative() {
        MessagePart payload = multipart("multipart/alternative",
                leaf("text/plain", "Versión texto"),
                leaf("text/html", "<p>Versión HTML</p>"));

        assertThat(parser.extractBody(payload)).isEqualTo("Versión texto");
    }

    @Test
    void findsTextPlainInNestedMultipartMixedAndSkipsAttachments() {
        MessagePart attachment = leaf("text/plain", "contenido del adjunto").setFilename("notas.txt");
        MessagePart payload = multipart("multipart/mixed",
                attachment,
                multipart("multipart/alternative",
                        leaf("text/html", "<p>HTML</p>"),
                        leaf("text/plain", "Cuerpo anidado")));

        assertThat(parser.extractBody(payload)).isEqualTo("Cuerpo anidado");
    }

    @Test
    void fallsBackToHtmlConvertedToText() {
        MessagePart payload = multipart("multipart/alternative",
                leaf("text/html", "<html><head><style>p{}</style></head><body>"
                        + "<p>Hola equipo &amp; socios.</p><div>¿Nos reunimos el lunes?<br>Saludos,<br>Ana</div>"
                        + "<script>alert(1)</script></body></html>"));

        assertThat(parser.extractBody(payload))
                .isEqualTo("Hola equipo & socios.\n¿Nos reunimos el lunes?\nSaludos,\nAna");
    }

    @Test
    void decodesBase64UrlSafeWithoutPadding() {
        // "¿?>~" produce '-' y '_' en Base64 URL-safe; se quita el padding como hace Gmail.
        String original = "Reunión ¿?>~ pagos";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(original.getBytes(StandardCharsets.UTF_8));
        assertThat(encoded).containsAnyOf("-", "_");

        assertThat(GmailMessageParser.decodeBase64Url(encoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void respectsCharsetFromContentTypeHeader() {
        Charset latin1 = StandardCharsets.ISO_8859_1;
        MessagePart part = new MessagePart()
                .setMimeType("text/plain")
                .setHeaders(List.of(header("Content-Type", "text/plain; charset=\"ISO-8859-1\"")))
                .setBody(new MessagePartBody().setData(encode("Módulo de pagos", latin1)));

        assertThat(parser.extractBody(part)).isEqualTo("Módulo de pagos");
    }

    @Test
    void invalidBase64ThrowsMimeDecodingException() {
        MessagePart part = new MessagePart().setMimeType("text/plain")
                .setBody(new MessagePartBody().setData("@@no-es-base64@@"));

        assertThatThrownBy(() -> parser.extractBody(part)).isInstanceOf(MimeDecodingException.class);
    }

    @Test
    void mapsHeadersAndSnippetToDto() {
        MessagePart payload = leaf("text/plain", "Cuerpo");
        payload.setHeaders(List.of(
                header("from", "Ana Torres <cliente.test@gmail.com>"),
                header("Subject", "Reunión módulo de pagos"),
                header("Date", "Fri, 25 Sep 2026 10:00:00 -0500")));
        Message message = new Message().setId("18c1").setThreadId("18c0")
                .setSnippet("Hola equipo &#39;UTP&#39;").setPayload(payload);

        GmailMessageDto dto = parser.toDto(message);

        assertThat(dto).isEqualTo(new GmailMessageDto("18c1", "18c0", "Ana Torres <cliente.test@gmail.com>",
                "Reunión módulo de pagos", "Fri, 25 Sep 2026 10:00:00 -0500", "Hola equipo 'UTP'", "Cuerpo"));
    }

    private static MessagePart leaf(String mimeType, String content) {
        return new MessagePart().setMimeType(mimeType)
                .setBody(new MessagePartBody().setData(encode(content, StandardCharsets.UTF_8)));
    }

    private static MessagePart multipart(String mimeType, MessagePart... parts) {
        return new MessagePart().setMimeType(mimeType).setBody(new MessagePartBody().setSize(0)).setParts(List.of(parts));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static String encode(String content, Charset charset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content.getBytes(charset));
    }
}
