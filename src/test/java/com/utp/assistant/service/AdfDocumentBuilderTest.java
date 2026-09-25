package com.utp.assistant.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdfDocumentBuilderTest {

    @Test
    void singleLineBecomesOneParagraph() {
        Map<String, Object> doc = AdfDocumentBuilder.fromPlainText("Revisión de requisitos técnicos para el módulo de pagos.");

        assertThat(doc).isEqualTo(Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", "Revisión de requisitos técnicos para el módulo de pagos."))))));
    }

    @Test
    void blankLinesSeparateParagraphsAndSingleBreaksBecomeHardBreaks() {
        Map<String, Object> doc = AdfDocumentBuilder.fromPlainText("Cliente: TechCorp\r\nMódulo: pagos\n\nAcordar reunión.");

        assertThat(doc.get("content")).isEqualTo(List.of(
                Map.of("type", "paragraph", "content", List.of(
                        Map.of("type", "text", "text", "Cliente: TechCorp"),
                        Map.of("type", "hardBreak"),
                        Map.of("type", "text", "text", "Módulo: pagos"))),
                Map.of("type", "paragraph", "content", List.of(
                        Map.of("type", "text", "text", "Acordar reunión.")))));
    }

    @Test
    void emptyTextProducesValidDocumentWithoutEmptyTextNodes() {
        Map<String, Object> doc = AdfDocumentBuilder.fromPlainText("   ");

        assertThat(doc.get("content")).isEqualTo(List.of(Map.of("type", "paragraph", "content", List.of())));
    }
}
