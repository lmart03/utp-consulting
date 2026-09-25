package com.utp.assistant.assistant.service;

import java.util.List;
import java.util.Map;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.utp.assistant.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.assistant.dto.RequestedToolCall;
import com.utp.assistant.assistant.exception.GeminiApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiResponseMapperTest {

    private final GeminiResponseMapper mapper = new GeminiResponseMapper();

    @Test
    void convertsFunctionCallsToRequestedToolCalls() {
        Map<String, Object> crmArgs = Map.of("name", "Ana Torres", "email", "luispachito.fergun@gmail.com", "status", "INTERESTED");
        Map<String, Object> calendarArgs = Map.of(
                "title", "Reunión módulo de pagos",
                "startDateTime", "2026-09-28T15:00:00-05:00",
                "endDateTime", "2026-09-28T16:00:00-05:00",
                "attendeeEmail", "luispachito.fergun@gmail.com");

        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(
                Part.fromText("TechCorp quiere reunirse el lunes."),
                Part.fromFunctionCall("actualizar_contacto_crm", crmArgs),
                Part.fromFunctionCall("agendar_reunion_google_calendar", calendarArgs)));

        assertThat(analysis.summary()).isEqualTo("TechCorp quiere reunirse el lunes.");
        assertThat(analysis.toolCalls()).containsExactly(
                new RequestedToolCall("actualizar_contacto_crm", crmArgs),
                new RequestedToolCall("agendar_reunion_google_calendar", calendarArgs));
        assertThat(analysis.rejectedToolCalls()).isEmpty();
    }

    @Test
    void summaryFunctionCallFillsSummaryAndIsNotAnAction() {
        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(
                Part.fromFunctionCall("registrar_resumen", Map.of("summary", "NovaTech pide reunión el lunes a las 3 p. m.")),
                Part.fromFunctionCall("crear_ticket_jira", Map.of("summary", "Seguimiento", "description", "Propuesta"))));

        assertThat(analysis.summary()).isEqualTo("NovaTech pide reunión el lunes a las 3 p. m.");
        assertThat(analysis.toolCalls()).extracting(RequestedToolCall::name).containsExactly("crear_ticket_jira");
        assertThat(analysis.rejectedToolCalls()).isEmpty();
    }

    @Test
    void summaryFunctionTakesPrecedenceOverFreeText() {
        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(
                Part.fromText("texto libre"),
                Part.fromFunctionCall("registrar_resumen", Map.of("summary", "Resumen estructurado"))));

        assertThat(analysis.summary()).isEqualTo("Resumen estructurado");
    }

    @Test
    void textOfReturnsPlainTextWithoutThoughts() {
        Part thought = Part.builder().text("razonamiento").thought(true).build();

        assertThat(mapper.textOf(response(thought, Part.fromText(" Resumen de respaldo. ")))).isEqualTo("Resumen de respaldo.");
    }

    @Test
    void responseWithoutFunctionCallsReturnsOnlySummary() {
        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(Part.fromText("Newsletter sin acciones.")));

        assertThat(analysis.summary()).isEqualTo("Newsletter sin acciones.");
        assertThat(analysis.toolCalls()).isEmpty();
        assertThat(analysis.rejectedToolCalls()).isEmpty();
    }

    @Test
    void rejectsUnknownTools() {
        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(
                Part.fromFunctionCall("borrar_base_de_datos", Map.of("confirm", true)),
                Part.fromFunctionCall("crear_ticket_jira", Map.of("summary", "Seguimiento", "description", "Propuesta"))));

        assertThat(analysis.toolCalls()).extracting(RequestedToolCall::name).containsExactly("crear_ticket_jira");
        assertThat(analysis.rejectedToolCalls()).containsExactly("borrar_base_de_datos");
    }

    @Test
    void ignoresThoughtParts() {
        Part thought = Part.builder().text("razonamiento interno").thought(true).build();

        GeminiEmailAnalysisResponse analysis = mapper.toAnalysis(response(thought, Part.fromText("Resumen")));

        assertThat(analysis.summary()).isEqualTo("Resumen");
    }

    @Test
    void responseWithoutCandidatesIsAnError() {
        assertThatThrownBy(() -> mapper.toAnalysis(GenerateContentResponse.builder().candidates(List.of()).build()))
                .isInstanceOf(GeminiApiException.class);
    }

    private static GenerateContentResponse response(Part... parts) {
        Candidate candidate = Candidate.builder()
                .content(Content.builder().role("model").parts(List.of(parts)).build())
                .build();
        return GenerateContentResponse.builder().candidates(List.of(candidate)).build();
    }
}
