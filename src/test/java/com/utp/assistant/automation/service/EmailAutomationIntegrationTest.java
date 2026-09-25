package com.utp.assistant.automation.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.utp.assistant.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.assistant.dto.RequestedToolCall;
import com.utp.assistant.assistant.exception.GeminiApiException;
import com.utp.assistant.assistant.service.GeminiService;
import com.utp.assistant.auth.service.GoogleAutomationAccount;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.EmailActionStatus;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.automation.entity.ProcessedEmailStatus;
import com.utp.assistant.automation.repository.EmailActionRepository;
import com.utp.assistant.crm.entity.Prospect;
import com.utp.assistant.crm.entity.ProspectStatus;
import com.utp.assistant.crm.repository.ProspectInteractionRepository;
import com.utp.assistant.crm.repository.ProspectRepository;
import com.utp.assistant.automation.repository.ProcessedEmailRepository;
import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.dto.CalendarEventResponse;
import com.utp.assistant.calendar.exception.CalendarApiException;
import com.utp.assistant.calendar.service.GoogleCalendarService;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.exception.GmailApiException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flujo end-to-end con PostgreSQL real (schema aislado "automation_test", no toca los datos de la demo) y
 * Gmail/Gemini/Jira/Calendar simulados. Requiere: docker compose up -d
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:2340}/${DB_NAME:utp_assistant}?currentSchema=automation_test",
        "spring.jpa.properties.hibernate.default_schema=automation_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.gemini.api-key=test-key",
        "app.jira.api-token=",
        "assistant.polling.enabled=false"
})
class EmailAutomationIntegrationTest {

    private static final String PRINCIPAL = "google-sub-123";
    private static final String TOKEN = "test-access-token";

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

    @Autowired
    private EmailAutomationService automationService;
    @Autowired
    private ProcessedEmailStore store;
    @Autowired
    private GoogleAutomationAccount automationAccount;
    @Autowired
    private ProcessedEmailRepository emailRepository;
    @Autowired
    private EmailActionRepository actionRepository;
    @Autowired
    private ProspectRepository prospectRepository;
    @Autowired
    private ProspectInteractionRepository interactionRepository;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        emailRepository.deleteAll();
        interactionRepository.deleteAll();
        prospectRepository.deleteAll();
        automationAccount.register(new GoogleAutomationAccount.Account(PRINCIPAL, "demo@gmail.com", OffsetDateTime.now()));
        when(tokenService.getAccessTokenWithScope(eq(PRINCIPAL), anyString())).thenReturn(TOKEN);
        when(jiraService.createIssue(any(JiraIssueRequest.class), anyList()))
                .thenReturn(new JiraIssueResponse("10023", "SCRUM-6", "self", "https://utp-tics.atlassian.net/browse/SCRUM-6", "S"));
        when(calendarService.createEvent(anyString(), any(CalendarEventRequest.class), anyString()))
                .thenReturn(event("evt-1"));
    }

    @Test
    void newEmailIsProcessedEndToEndAndMarkedAsRead() {
        inbox(email("m1", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());

        EmailAutomationService.CycleResult result = automationService.runCycle();

        assertThat(result.executed()).isTrue();
        assertThat(result.newEmails()).isEqualTo(1);
        ProcessedEmail email = emailRepository.findByGmailMessageId("m1").orElseThrow();
        assertThat(email.getStatus()).isEqualTo(ProcessedEmailStatus.PROCESSED);
        assertThat(email.isGmailMarkedRead()).isTrue();
        assertThat(email.getAiSummary()).isEqualTo("TechCorp quiere reunirse el lunes.");
        assertThat(actionsByTool("m1")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "crear_ticket_jira", EmailActionStatus.SUCCESS,
                "agendar_reunion_google_calendar", EmailActionStatus.SUCCESS,
                "actualizar_contacto_crm", EmailActionStatus.SUCCESS));

        EmailAction jira = action("m1", "crear_ticket_jira");
        assertThat(jira.getExternalId()).isEqualTo("SCRUM-6");
        assertThat(action("m1", "agendar_reunion_google_calendar").getExternalId()).isEqualTo("evt-1");
        // CRM: contacto creado con el email real del From y MEETING_SCHEDULED porque el correo pidió reunión.
        Prospect prospect = prospectRepository.findByEmail("ana.torres@techcorp.com").orElseThrow();
        assertThat(action("m1", "actualizar_contacto_crm").getExternalId()).isEqualTo(String.valueOf(prospect.getId()));
        assertThat(prospect.getName()).isEqualTo("Ana Torres");
        assertThat(prospect.getStatus()).isEqualTo(ProspectStatus.MEETING_SCHEDULED);
        assertThat(prospect.getInteractionCount()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> labels = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<JiraIssueRequest> jiraRequest = ArgumentCaptor.forClass(JiraIssueRequest.class);
        verify(jiraService).createIssue(jiraRequest.capture(), labels.capture());
        assertThat(labels.getValue()).containsExactly("utp-gmail-m1");
        assertThat(jiraRequest.getValue().description()).contains("UTP-GMAIL-ID: m1");
        verify(calendarService).createEvent(eq(TOKEN), any(CalendarEventRequest.class), eq("m1"));
        verify(gmailService).markAsRead(TOKEN, "m1");
    }

    @Test
    void repeatedCyclesNeverDuplicateJiraCalendarOrProcessedEmail() {
        inbox(email("m1", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());

        automationService.runCycle();
        automationService.runCycle();
        automationService.runCycle();

        assertThat(emailRepository.count()).isEqualTo(1);
        verify(geminiService, times(1)).analyzeEmail(any());
        verify(jiraService, times(1)).createIssue(any(JiraIssueRequest.class), anyList());
        verify(calendarService, times(1)).createEvent(anyString(), any(CalendarEventRequest.class), anyString());
    }

    @Test
    void emailWithoutToolCallsIsIgnoredAndMarkedAsRead() {
        inbox(email("m2", Instant.now()));
        when(geminiService.analyzeEmail(any()))
                .thenReturn(new GeminiEmailAnalysisResponse("Agradecimiento sin pedido.", List.of(), List.of()));

        automationService.runCycle();

        ProcessedEmail email = emailRepository.findByGmailMessageId("m2").orElseThrow();
        assertThat(email.getStatus()).isEqualTo(ProcessedEmailStatus.IGNORED);
        assertThat(actionRepository.findByProcessedEmailIdOrderByIdAsc(email.getId())).isEmpty();
        verify(gmailService).markAsRead(TOKEN, "m2");
        verify(jiraService, never()).createIssue(any(JiraIssueRequest.class), anyList());
    }

    @Test
    void partialFailureDoesNotMarkReadAndRetryOnlyRunsTheFailedTool() {
        inbox(email("m3", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());
        when(calendarService.createEvent(anyString(), any(CalendarEventRequest.class), anyString()))
                .thenThrow(new CalendarApiException("Calendar caído"))
                .thenReturn(event("evt-2"));
        when(calendarService.findEventByGmailMessageId(TOKEN, "m3")).thenReturn(Optional.empty());

        automationService.runCycle();

        ProcessedEmail partial = emailRepository.findByGmailMessageId("m3").orElseThrow();
        assertThat(partial.getStatus()).isEqualTo(ProcessedEmailStatus.PARTIAL);
        assertThat(partial.isGmailMarkedRead()).isFalse();
        assertThat(partial.getNextRetryAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        assertThat(actionsByTool("m3")).containsEntry("crear_ticket_jira", EmailActionStatus.SUCCESS)
                .containsEntry("agendar_reunion_google_calendar", EmailActionStatus.FAILED);
        verify(gmailService, never()).markAsRead(anyString(), anyString());

        automationService.runCycle(); // aún no vence el reintento: no pasa nada
        verify(calendarService, times(1)).createEvent(anyString(), any(CalendarEventRequest.class), anyString());

        makeDue("m3");
        automationService.runCycle();

        ProcessedEmail processed = emailRepository.findByGmailMessageId("m3").orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(ProcessedEmailStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(2);
        assertThat(action("m3", "agendar_reunion_google_calendar").getExternalId()).isEqualTo("evt-2");
        verify(geminiService, times(1)).analyzeEmail(any());
        verify(jiraService, times(1)).createIssue(any(JiraIssueRequest.class), anyList());
        verify(jiraService, never()).findIssueByLabel(anyString());
        verify(calendarService, times(2)).createEvent(anyString(), any(CalendarEventRequest.class), anyString());
        verify(gmailService).markAsRead(TOKEN, "m3");
    }

    @Test
    void calendarRetryReusesEventCreatedBeforeTheFailure() {
        inbox(email("m4", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());
        when(calendarService.createEvent(anyString(), any(CalendarEventRequest.class), anyString()))
                .thenThrow(new CalendarApiException("timeout"));
        when(calendarService.findEventByGmailMessageId(TOKEN, "m4")).thenReturn(Optional.of(event("evt-existing")));

        automationService.runCycle();
        makeDue("m4");
        automationService.runCycle();

        assertThat(emailRepository.findByGmailMessageId("m4").orElseThrow().getStatus()).isEqualTo(ProcessedEmailStatus.PROCESSED);
        assertThat(action("m4", "agendar_reunion_google_calendar").getExternalId()).isEqualTo("evt-existing");
        verify(calendarService, times(1)).createEvent(anyString(), any(CalendarEventRequest.class), anyString());
    }

    @Test
    void jiraRetryReusesIssueFoundByLabel() {
        inbox(email("m5", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());
        when(jiraService.createIssue(any(JiraIssueRequest.class), anyList()))
                .thenThrow(new com.utp.assistant.jira.exception.JiraApiException(HttpStatus.BAD_GATEWAY, "timeout"));
        when(jiraService.findIssueByLabel("utp-gmail-m5"))
                .thenReturn(Optional.of(new JiraIssueResponse("1", "SCRUM-9", "self", "url", "S")));

        automationService.runCycle();
        makeDue("m5");
        automationService.runCycle();

        assertThat(action("m5", "crear_ticket_jira").getExternalId()).isEqualTo("SCRUM-9");
        assertThat(action("m5", "crear_ticket_jira").getStatus()).isEqualTo(EmailActionStatus.SUCCESS);
        verify(jiraService, times(1)).createIssue(any(JiraIssueRequest.class), anyList());
    }

    @Test
    void markAsReadFailureKeepsProcessedAndRetriesOnlyGmail() {
        inbox(email("m6", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenReturn(meetingAnalysis());
        doThrow(new GmailApiException("Gmail no disponible")).doNothing().when(gmailService).markAsRead(TOKEN, "m6");

        automationService.runCycle();

        ProcessedEmail processed = emailRepository.findByGmailMessageId("m6").orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(ProcessedEmailStatus.PROCESSED);
        assertThat(processed.isGmailMarkedRead()).isFalse();
        assertThat(processed.getErrorMessage()).contains("marcar como leído");

        makeDue("m6");
        automationService.runCycle();

        assertThat(emailRepository.findByGmailMessageId("m6").orElseThrow().isGmailMarkedRead()).isTrue();
        verify(gmailService, times(2)).markAsRead(TOKEN, "m6");
        verify(geminiService, times(1)).analyzeEmail(any());
        verify(jiraService, times(1)).createIssue(any(JiraIssueRequest.class), anyList());
        verify(calendarService, times(1)).createEvent(anyString(), any(CalendarEventRequest.class), anyString());
    }

    @Test
    void failedAnalysisIsRetriedWithBackoffUntilMaxRetries() {
        inbox(email("m7", Instant.now()));
        when(gmailService.getReceivedEmail(TOKEN, "m7")).thenReturn(email("m7", Instant.now()));
        when(geminiService.analyzeEmail(any())).thenThrow(new GeminiApiException(HttpStatus.SERVICE_UNAVAILABLE, "saturado"));

        automationService.runCycle();
        for (int retry = 0; retry < 3; retry++) {
            ProcessedEmail pending = emailRepository.findByGmailMessageId("m7").orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(ProcessedEmailStatus.FAILED);
            assertThat(pending.getNextRetryAt()).isNotNull();
            makeDue("m7");
            automationService.runCycle();
        }

        ProcessedEmail failed = emailRepository.findByGmailMessageId("m7").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ProcessedEmailStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(4);
        assertThat(failed.getNextRetryAt()).isNull();
        assertThat(failed.getErrorMessage()).contains("Máximo de reintentos");
        verify(geminiService, times(4)).analyzeEmail(any());
        verify(gmailService, never()).markAsRead(anyString(), anyString());
    }

    @Test
    void emailsReceivedBeforeAutomationStartAreNotProcessed() {
        Instant beforeStart = automationService.automationStartedAt().minusSeconds(3600);
        inbox(email("old", beforeStart));

        automationService.runCycle();

        assertThat(emailRepository.existsByGmailMessageId("old")).isFalse();
        verify(geminiService, never()).analyzeEmail(any());
        assertThat(automationService.gmailQuery())
                .isEqualTo("in:inbox is:unread after:" + automationService.automationStartedAt().getEpochSecond());
    }

    @Test
    void concurrentClaimsOfTheSameEmailHaveExactlyOneWinner() throws Exception {
        ReceivedEmail received = email("m8", Instant.now());
        OffsetDateTime lease = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Optional<ProcessedEmail>>> claims = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                claims.add(pool.submit(() -> {
                    start.await();
                    return store.tryClaim(received, lease);
                }));
            }
            start.countDown();
            int winners = 0;
            for (Future<Optional<ProcessedEmail>> claim : claims) {
                winners += claim.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(store.tryClaim(received, lease)).isEmpty();
        assertThat(emailRepository.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateGmailMessageIdAndDuplicateToolPerEmail() {
        ProcessedEmail email = store.tryClaim(email("m9", Instant.now()), OffsetDateTime.now(ZoneOffset.UTC)).orElseThrow();

        ProcessedEmail duplicate = new ProcessedEmail();
        duplicate.setGmailMessageId("m9");
        duplicate.setStatus(ProcessedEmailStatus.PROCESSING);
        assertThatThrownBy(() -> emailRepository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);

        actionRepository.saveAndFlush(newAction(email, "crear_ticket_jira"));
        assertThatThrownBy(() -> actionRepository.saveAndFlush(newAction(email, "crear_ticket_jira")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void overlappingCyclesAreRejectedByTheLocalLock() throws Exception {
        CountDownLatch insideCycle = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gmailService.listMessageIds(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            insideCycle.countDown();
            release.await(10, TimeUnit.SECONDS);
            return List.of();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<EmailAutomationService.CycleResult> first = pool.submit(automationService::runCycle);
            assertThat(insideCycle.await(10, TimeUnit.SECONDS)).isTrue();

            EmailAutomationService.CycleResult second = automationService.runCycle();
            assertThat(second.executed()).isFalse();
            assertThat(second.message()).contains("Ya hay un ciclo");

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).executed()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void cycleWaitsWhenNoGoogleAccountIsRegistered() {
        automationAccount.register((GoogleAutomationAccount.Account) null);

        EmailAutomationService.CycleResult result = automationService.runCycle();

        assertThat(result.executed()).isFalse();
        assertThat(result.message()).contains("/oauth2/authorization/google");
        verify(gmailService, never()).listMessageIds(anyString(), anyString(), anyInt());
    }

    private void inbox(ReceivedEmail... emails) {
        when(gmailService.listMessageIds(eq(TOKEN), anyString(), anyInt()))
                .thenReturn(java.util.Arrays.stream(emails).map(e -> e.message().id()).toList());
        for (ReceivedEmail email : emails) {
            when(gmailService.getReceivedEmail(TOKEN, email.message().id())).thenReturn(email);
        }
    }

    private void makeDue(String gmailMessageId) {
        ProcessedEmail email = emailRepository.findByGmailMessageId(gmailMessageId).orElseThrow();
        email.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        emailRepository.saveAndFlush(email);
    }

    private Map<String, EmailActionStatus> actionsByTool(String gmailMessageId) {
        Long id = emailRepository.findByGmailMessageId(gmailMessageId).orElseThrow().getId();
        Map<String, EmailActionStatus> result = new java.util.HashMap<>();
        actionRepository.findByProcessedEmailIdOrderByIdAsc(id).forEach(a -> result.put(a.getToolName(), a.getStatus()));
        return result;
    }

    private EmailAction action(String gmailMessageId, String tool) {
        Long id = emailRepository.findByGmailMessageId(gmailMessageId).orElseThrow().getId();
        return actionRepository.findByProcessedEmailIdOrderByIdAsc(id).stream()
                .filter(a -> a.getToolName().equals(tool)).findFirst().orElseThrow();
    }

    private static EmailAction newAction(ProcessedEmail email, String tool) {
        EmailAction action = new EmailAction();
        action.setProcessedEmail(email);
        action.setToolName(tool);
        action.setStatus(EmailActionStatus.PENDING);
        return action;
    }

    private static ReceivedEmail email(String id, Instant receivedAt) {
        return new ReceivedEmail(new GmailMessageDto(id, id, "Ana Torres <ana.torres@techcorp.com>",
                "Reunión módulo de pagos TechCorp", "Fri, 25 Sep 2026 12:33:19 -0500", "Hola equipo",
                "¿Podemos reunirnos el lunes 28 de septiembre de 2026 a las 3:00 p. m.?"), receivedAt);
    }

    private static GeminiEmailAnalysisResponse meetingAnalysis() {
        return new GeminiEmailAnalysisResponse("TechCorp quiere reunirse el lunes.", List.of(
                new RequestedToolCall("actualizar_contacto_crm",
                        Map.of("name", "Ana Torres", "email", "ana.torres@techcorp.com", "status", "INTERESTED")),
                new RequestedToolCall("crear_ticket_jira",
                        Map.of("summary", "Revisión requisitos técnicos - TechCorp", "description", "Revisar requisitos.")),
                new RequestedToolCall("agendar_reunion_google_calendar", Map.of(
                        "title", "Reunión módulo de pagos - TechCorp",
                        "startDateTime", "2026-09-28T15:00:00-05:00",
                        "endDateTime", "2026-09-28T16:00:00-05:00",
                        "attendeeEmail", "ana.torres@techcorp.com"))),
                List.of());
    }

    private static CalendarEventResponse event(String id) {
        return new CalendarEventResponse(id, "https://calendar.google.com/event?eid=" + id, "confirmed",
                "Reunión módulo de pagos - TechCorp", "2026-09-28T15:00:00.000-05:00", "2026-09-28T16:00:00.000-05:00");
    }
}
