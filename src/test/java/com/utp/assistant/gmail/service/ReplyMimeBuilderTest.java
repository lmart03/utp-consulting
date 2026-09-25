package com.utp.assistant.gmail.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyMimeBuilderTest {

    @Test
    void buildsThreadedPlainTextReplyWithEncodedSubjectAndBody() {
        String mime = ReplyMimeBuilder.build("ana@techcorp.com", "Reunión módulo de pagos",
                "<abc@mail.gmail.com>", "<root@mail.gmail.com>", "Hola Ana,\nconfirmamos la reunión.");

        assertThat(mime).contains("To: ana@techcorp.com\r\n");
        assertThat(mime).contains("Subject: =?UTF-8?B?"
                + Base64.getEncoder().encodeToString("Re: Reunión módulo de pagos".getBytes(StandardCharsets.UTF_8)) + "?=\r\n");
        assertThat(mime).contains("In-Reply-To: <abc@mail.gmail.com>\r\n");
        assertThat(mime).contains("References: <root@mail.gmail.com> <abc@mail.gmail.com>\r\n");
        assertThat(mime).contains("Content-Type: text/plain; charset=UTF-8\r\n");

        String encodedBody = mime.substring(mime.indexOf("\r\n\r\n") + 4);
        assertThat(new String(Base64.getMimeDecoder().decode(encodedBody), StandardCharsets.UTF_8))
                .isEqualTo("Hola Ana,\r\nconfirmamos la reunión.");
    }

    @Test
    void headerValuesFromThirdPartiesCannotInjectHeaders() {
        String mime = ReplyMimeBuilder.build("ana@techcorp.com\r\nBcc: spy@evil.com", "Hola\r\nBcc: spy@evil.com",
                null, null, "Texto");

        assertThat(mime).doesNotContain("\r\nBcc:");
        assertThat(mime).doesNotContain("In-Reply-To");
    }

    @Test
    void doesNotDuplicateReplyPrefix() {
        assertThat(ReplyMimeBuilder.replySubject("RE: Propuesta")).isEqualTo("RE: Propuesta");
        assertThat(ReplyMimeBuilder.replySubject(null)).isEqualTo("Re: (sin asunto)");
    }
}
