package com.utp.assistant.automation.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.utp.assistant.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.assistant.dto.RequestedToolCall;
import com.utp.assistant.assistant.service.GeminiService;
import com.utp.assistant.auth.service.GoogleAutomationAccount;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.automation.dto.AutomationEvent;
import com.utp.assistant.automation.dto.AutomationEventStatus;
import com.utp.assistant.automation.dto.AutomationStage;
import com.utp.assistant.automation.repository.EmailActionRepository;
import com.utp.assistant.automation.repository.ProcessedEmailRepository;
import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.dto.CalendarEventResponse;
import com.utp.assistant.calendar.exception.CalendarApiException;
import com.utp.assistant.calendar.service.GoogleCalendarService;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.service.GmailService;
import com.utp.assistant.gmail.service.ReceivedEmail;
import com.utp.assistant.jira.dto.JiraIssueRequest;
import com.utp.assistant.jira.dto.JiraIssueResponse;
import com.utp.assistant.jira.service.JiraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:2340}/${DB_NAME:utp_assistant}?currentSchema=observability_test",
        "spring.jpa.properties.hibernate.default_schema=observability_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.gemini.api-key=test-key",
        "app.jira.api-token=",
        "assistant.polling.enabled=false"
})
class ObservabilityFlowIntegrationTest {

    private static final String PRINCIPAL = "google-sub-obs";
    private static final String TOKEN = "test-token";

    @MockitoBean
    private GmailService gmailService;
    @MockitoBean
    private GeminiService geminiService;
    @MockitoBean
    private JiraService jiraService;
    @MockitoBean
    private GoogleCalendarService calendarService;
    @MockitoBean
    private GoogleTokenService tokenService;
    @MockitoBean
    private AutomationEventPublisher publisher;

    @Autowired
    private EmailAutomationService automationService;
    @Autowired
    private GoogleAutomationAccount automationAccount;
    @Autowired
    private ProcessedEmailRepository emailRepository;
    @Autowired
    private EmailActionRepository actionRepository;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        emailRepository.deleteAll();
        automationAccount.register(new GoogleAutomationAccount.Account(PRINCIPAL, "demo@gmail.com", OffsetDateTime.now()));
        when(tokenService.getAccessTokenWithScope(eq(PRINCIPAL), anyString())).thenReturn(TOKEN);
        when(jiraService.createIssue(any(JiraIssueRequest.class), anyList()))
                .thenReturn(new JiraIssueResponse("10023", "SCRUM-14", "self", "https://utp-tics.atlassian.net/browse/SCRUM-14", "S"));
        when(calendarService.createEvent(anyString(), any(CalendarEventRequest.class), anyString()))
                .thenReturn(new CalendarEventResponse("cal-14", "https://calendar.google.com/event?eid=cal-14", "confirmed",
                        "Reunión NovaTech", "2026-09-26T15:00:00-05:00", "2026-09-26T16:00:00-05:00"));
    }

    @Test
    void completePipelinePublishesAllExpectedObservabilityStages() {
        String msgId = "obs-1";
        ReceivedEmail received = email(msgId, Instant.now());
        when(gmailService.listMessageIds(eq(TOKEN), anyString(), anyInt())).thenReturn(List.of(msgId));
        when(gmailService.getReceivedEmail(TOKEN, msgId)).thenReturn(received);
        when(geminiService.analyzeEmail(any())).thenReturn(new GeminiEmailAnalysisResponse(
                "NovaTech solicita revisión técnica y reunión.",
                List.of(
                        new RequestedToolCall("actualizar_contacto_crm", Map.of("company", "NovaTech", "email", "cliente@novatech.com")),
                        new RequestedToolCall("crear_ticket_jira", Map.of("summary", "Revisión técnica", "description", "Revisar")),
                        new RequestedToolCall("agendar_reunion_google_calendar", Map.of(
                                "title", "Reunión NovaTech",
                                "startDateTime", "2026-09-26T15:00:00-05:00",
                                "endDateTime", "2026-09-26T16:00:00-05:00"))
                ),
                List.of()
        ));

        automationService.runCycle();

        ArgumentCaptor<AutomationEvent> eventCaptor = ArgumentCaptor.forClass(AutomationEvent.class);
        verify(publisher, atLeastOnce()).publish(eventCaptor.capture());

        List<AutomationEvent> events = eventCaptor.getAllValues();
        List<AutomationStage> stages = events.stream().map(AutomationEvent::stage).toList();

        assertThat(stages).contains(
                AutomationStage.EMAIL_DETECTED,
                AutomationStage.EMAIL_CLAIMED,
                AutomationStage.AI_ANALYSIS_STARTED,
                AutomationStage.AI_ANALYSIS_COMPLETED,
                AutomationStage.TOOL_STARTED,
                AutomationStage.TOOL_COMPLETED,
                AutomationStage.MARK_READ_STARTED,
                AutomationStage.MARK_READ_COMPLETED,
                AutomationStage.PROCESS_COMPLETED
        );

        AutomationEvent completedEvent = events.stream()
                .filter(e -> e.stage() == AutomationStage.PROCESS_COMPLETED)
                .findFirst()
                .orElseThrow();
        assertThat(completedEvent.status()).isEqualTo(AutomationEventStatus.SUCCESS);
        assertThat(completedEvent.elapsedMs()).isNotNull().isGreaterThanOrEqualTo(0L);

        AutomationEvent aiCompleted = events.stream()
                .filter(e -> e.stage() == AutomationStage.AI_ANALYSIS_COMPLETED)
                .findFirst()
                .orElseThrow();
        assertThat(aiCompleted.detectedCompany()).isEqualTo("NovaTech");
        assertThat(aiCompleted.aiSummary()).contains("NovaTech");

        AutomationEvent jiraCompleted = events.stream()
                .filter(e -> e.stage() == AutomationStage.TOOL_COMPLETED && "crear_ticket_jira".equals(e.toolName()))
                .findFirst()
                .orElseThrow();
        assertThat(jiraCompleted.externalId()).isEqualTo("SCRUM-14");
    }

    @Test
    void toolFailurePublishesToolFailedWithSafeError() {
        String msgId = "obs-fail";
        ReceivedEmail received = email(msgId, Instant.now());
        when(gmailService.listMessageIds(eq(TOKEN), anyString(), anyInt())).thenReturn(List.of(msgId));
        when(gmailService.getReceivedEmail(TOKEN, msgId)).thenReturn(received);
        when(geminiService.analyzeEmail(any())).thenReturn(new GeminiEmailAnalysisResponse(
                "Solicitud de reunión.",
                List.of(new RequestedToolCall("agendar_reunion_google_calendar", Map.of(
                        "title", "Reunión",
                        "startDateTime", "2026-09-26T15:00:00-05:00",
                        "endDateTime", "2026-09-26T16:00:00-05:00"))),
                List.of()
        ));
        when(calendarService.createEvent(anyString(), any(CalendarEventRequest.class), anyString()))
                .thenThrow(new CalendarApiException("Google Calendar temporalmente no disponible"));

        automationService.runCycle();

        ArgumentCaptor<AutomationEvent> eventCaptor = ArgumentCaptor.forClass(AutomationEvent.class);
        verify(publisher, atLeastOnce()).publish(eventCaptor.capture());

        List<AutomationEvent> events = eventCaptor.getAllValues();
        AutomationEvent toolFailed = events.stream()
                .filter(e -> e.stage() == AutomationStage.TOOL_FAILED)
                .findFirst()
                .orElseThrow();

        assertThat(toolFailed.status()).isEqualTo(AutomationEventStatus.FAILED);
        assertThat(toolFailed.toolName()).isEqualTo("agendar_reunion_google_calendar");
        assertThat(toolFailed.error()).contains("Google Calendar temporalmente no disponible");
        assertThat(toolFailed.metadata()).containsKey("attempt");
    }

    private static ReceivedEmail email(String id, Instant receivedAt) {
        return new ReceivedEmail(new GmailMessageDto(id, id, "cliente@novatech.com",
                "Reunión seguimiento NovaTech", "Fri, 25 Sep 2026 12:33:19 -0500", "Hola equipo",
                "¿Podemos reunirnos mañana a las 3:00 p. m.?"), receivedAt);
    }
}
