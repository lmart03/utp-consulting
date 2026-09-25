package com.utp.assistant.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Convierte texto plano a Atlassian Document Format (ADF), requerido por Jira REST API v3 en campos
 * multilínea como description. Líneas en blanco separan párrafos; saltos simples se vuelven hardBreak.
 */
public final class AdfDocumentBuilder {

    private AdfDocumentBuilder() {
    }

    public static Map<String, Object> fromPlainText(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').strip();

        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (String block : normalized.split("\\n\\s*\\n")) {
            List<Map<String, Object>> inline = paragraphContent(block);
            if (!inline.isEmpty()) {
                paragraphs.add(Map.of("type", "paragraph", "content", inline));
            }
        }
        if (paragraphs.isEmpty()) {
            // ADF no admite nodos text vacíos: documento con un párrafo vacío.
            paragraphs.add(Map.of("type", "paragraph", "content", List.of()));
        }
        return Map.of("type", "doc", "version", 1, "content", paragraphs);
    }

    private static List<Map<String, Object>> paragraphContent(String block) {
        List<Map<String, Object>> inline = new ArrayList<>();
        for (String line : block.split("\\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!inline.isEmpty()) {
                inline.add(Map.of("type", "hardBreak"));
            }
            inline.add(Map.of("type", "text", "text", trimmed));
        }
        return inline;
    }
}
