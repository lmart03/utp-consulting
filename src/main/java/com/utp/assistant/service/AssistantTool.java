package com.utp.assistant.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Allowlist de herramientas que Gemini puede solicitar. Cualquier otro nombre se rechaza.
 */
public enum AssistantTool {

    ACTUALIZAR_CONTACTO_CRM("actualizar_contacto_crm"),
    CREAR_TICKET_JIRA("crear_ticket_jira"),
    AGENDAR_REUNION_GOOGLE_CALENDAR("agendar_reunion_google_calendar");

    private final String functionName;

    AssistantTool(String functionName) {
        this.functionName = functionName;
    }

    public String functionName() {
        return functionName;
    }

    public static Optional<AssistantTool> fromFunctionName(String name) {
        return Arrays.stream(values())
                .filter(tool -> tool.functionName.equals(name))
                .findFirst();
    }
}
