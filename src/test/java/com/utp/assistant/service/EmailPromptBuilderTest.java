package com.utp.assistant.service;

import java.time.ZoneId;

import com.utp.assistant.config.GeminiProperties;
import com.utp.assistant.dto.AnalyzeEmailRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPromptBuilderTest {

    private final EmailPromptBuilder builder = new EmailPromptBuilder(
            new GeminiProperties("", "gemini-test", null, 1, 1000, ZoneId.of("America/Lima"), null));

    @Test
    void includesReceivedDateInBusinessTimeZoneAndRealSender() {
        String prompt = builder.build(new AnalyzeEmailRequest("1a0d", "1a0d",
                "FerGun 132 <luispachito.fergun@gmail.com>", "Reunión módulo de pagos",
                "Fri, 25 Sep 2026 17:22:05 +0000 (UTC)", "¿Podemos reunirnos el lunes a las 3:00 p.m.?"));

        assertThat(prompt)
                .contains("Zona horaria empresarial: America/Lima")
                .contains("2026-09-25T12:22:05-05:00 (viernes)")
                .contains("Email real del remitente: luispachito.fergun@gmail.com")
                .contains("<<<INICIO_CORREO\n¿Podemos reunirnos el lunes a las 3:00 p.m.?\nFIN_CORREO>>>");
    }

    @Test
    void unparseableDateIsPassedRawWithoutNormalizedLine() {
        String prompt = builder.build(new AnalyzeEmailRequest(null, null, "a@b.com", null, "ayer", "Hola"));

        assertThat(prompt).contains("Fecha de recepción (header Date): ayer").doesNotContain("Fecha de recepción en");
    }

    @Test
    void extractsSenderAddress() {
        assertThat(EmailPromptBuilder.senderAddress("Ana <ana@techcorp.com>")).isEqualTo("ana@techcorp.com");
        assertThat(EmailPromptBuilder.senderAddress(" ana@techcorp.com ")).isEqualTo("ana@techcorp.com");
    }
}
