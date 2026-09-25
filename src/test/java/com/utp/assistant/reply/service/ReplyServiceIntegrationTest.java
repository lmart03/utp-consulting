package com.utp.assistant.reply.service;

import java.util.List;

import com.utp.assistant.assistant.service.GeminiService;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.exception.GmailApiException;
import com.utp.assistant.gmail.service.GmailService;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import com.utp.assistant.reply.exception.InvalidReplyStateException;
import com.utp.assistant.reply.repository.EmailReplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Borradores y envío con PostgreSQL real en un schema aislado ("reply_test"). Requiere: docker compose up -d */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:2340}/${DB_NAME:utp_assistant}?currentSchema=reply_test",
        "spring.jpa.properties.hibernate.default_schema=reply_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.gemini.api-key=test-key",
        "app.jira.api-token=",
        "assistant.polling.enabled=false"
})
class ReplyServiceIntegrationTest {

    private static final String TOKEN = "user-token";
    private static final Authentication USER = new TestingAuthenticationToken("google-sub-1", null, "ROLE_USER");

    @MockitoBean
    private GeminiService geminiService;
    @MockitoBean
    private GmailService gmailService;
    @MockitoBean
    private GoogleTokenService tokenService;

    @Autowired
    private ReplyService replyService;
    @Autowired
    private EmailReplyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(tokenService.getAccessTokenWithScope(eq(USER), anyString())).thenReturn(TOKEN);
        when(geminiService.draftReply(any())).thenReturn("Hola Carla,\n\nGracias por escribirnos.\n\nEquipo UTP Consult");
    }

    @Test
    void createDraftIsIdempotentPerEmail() {
        EmailReply first = replyService.createDraft(command(1L));
        EmailReply second = replyService.createDraft(command(1L));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(ReplyStatus.DRAFT);
        assertThat(first.getToAddress()).isEqualTo("carla@andina.com");
        assertThat(first.getSubject()).isEqualTo("Re: Cotización");
        verify(geminiService, times(1)).draftReply(any());
    }

    @Test
    void sendsEditedTextInTheOriginalThreadOnlyOnce() {
        EmailReply draft = replyService.createDraft(command(2L));
        when(gmailService.sendReply(TOKEN, "g-2", "t-2", "carla@andina.com", "Re: Cotización", "Texto editado"))
                .thenReturn("sent-1");

        EmailReply sent = replyService.send(draft.getId(), "  Texto editado  ", USER);

        assertThat(sent.getStatus()).isEqualTo(ReplyStatus.SENT);
        assertThat(sent.getBody()).isEqualTo("Texto editado");
        assertThat(sent.getAiBody()).startsWith("Hola Carla");
        assertThat(sent.getSentGmailMessageId()).isEqualTo("sent-1");
        assertThat(sent.getSentAt()).isNotNull();
        assertThatThrownBy(() -> replyService.send(draft.getId(), "Otra vez", USER))
                .isInstanceOf(InvalidReplyStateException.class);
        verify(gmailService, times(1)).sendReply(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void gmailFailureKeepsTheEditedDraftForRetry() {
        EmailReply draft = replyService.createDraft(command(3L));
        when(gmailService.sendReply(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new GmailApiException("Gmail no disponible", null));

        assertThatThrownBy(() -> replyService.send(draft.getId(), "Texto editado", USER))
                .isInstanceOf(GmailApiException.class);

        EmailReply after = replyService.get(draft.getId());
        assertThat(after.getStatus()).isEqualTo(ReplyStatus.DRAFT);
        assertThat(after.getBody()).isEqualTo("Texto editado");
        assertThat(after.getErrorMessage()).contains("Gmail no disponible");
    }

    @Test
    void failedDraftCanBeRegeneratedAndDiscarded() {
        when(geminiService.draftReply(any())).thenReturn("  ").thenReturn("Nuevo borrador");
        EmailReply failed = replyService.createDraft(command(4L));
        assertThat(failed.getStatus()).isEqualTo(ReplyStatus.FAILED);

        when(gmailService.getMessage(USER, "g-4")).thenReturn(new GmailMessageDto("g-4", "t-4",
                "Carla <carla@andina.com>", "Cotización", "Fri, 25 Sep 2026 12:00:00 -0500", "snip", "Cuerpo"));
        EmailReply regenerated = replyService.regenerate(failed.getId(), USER);
        assertThat(regenerated.getStatus()).isEqualTo(ReplyStatus.DRAFT);
        assertThat(regenerated.getBody()).isEqualTo("Nuevo borrador");

        assertThat(replyService.discard(failed.getId()).getStatus()).isEqualTo(ReplyStatus.DISCARDED);
        assertThatThrownBy(() -> replyService.send(failed.getId(), "x", USER)).isInstanceOf(InvalidReplyStateException.class);
    }

    @Test
    void skipsAutomatedSenders() {
        assertThat(replyService.skipReason("Banco <no-reply@banco.com>")).isPresent();
        assertThat(replyService.skipReason("MAILER-DAEMON@google.com")).isPresent();
        assertThat(replyService.skipReason("Carla <carla@andina.com>")).isEmpty();
    }

    private static ReplyDraftCommand command(long emailId) {
        GmailMessageDto message = new GmailMessageDto("g-" + emailId, "t-" + emailId, "Carla <Carla@Andina.com>",
                "Cotización", "Fri, 25 Sep 2026 12:00:00 -0500", "snip", "Necesitamos una cotización.");
        return new ReplyDraftCommand(emailId, "g-" + emailId, "t-" + emailId, "Carla <Carla@Andina.com>", "Cotización",
                "Carla pide cotización.", List.of("Número de seguimiento SCRUM-1"), message, "google-sub-1");
    }
}
