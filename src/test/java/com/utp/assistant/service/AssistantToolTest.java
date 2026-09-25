package com.utp.assistant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantToolTest {

    @Test
    void allowlistContainsOnlyTheThreeBusinessTools() {
        assertThat(AssistantTool.values()).extracting(AssistantTool::functionName)
                .containsExactlyInAnyOrder("actualizar_contacto_crm", "crear_ticket_jira", "agendar_reunion_google_calendar");
    }

    @Test
    void resolvesOnlyExactNames() {
        assertThat(AssistantTool.fromFunctionName("crear_ticket_jira")).contains(AssistantTool.CREAR_TICKET_JIRA);
        assertThat(AssistantTool.fromFunctionName("CREAR_TICKET_JIRA")).isEmpty();
        assertThat(AssistantTool.fromFunctionName("enviar_email")).isEmpty();
        assertThat(AssistantTool.fromFunctionName(null)).isEmpty();
    }
}
