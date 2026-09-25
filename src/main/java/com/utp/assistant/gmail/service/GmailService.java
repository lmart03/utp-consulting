package com.utp.assistant.gmail.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.utp.assistant.auth.exception.GmailAuthorizationException;
import com.utp.assistant.auth.exception.GoogleScopeMissingException;
import com.utp.assistant.auth.service.GoogleTokenService;
import com.utp.assistant.gmail.config.GmailProperties;
import com.utp.assistant.gmail.dto.GmailMessageDto;
import com.utp.assistant.gmail.exception.GmailApiException;
import com.utp.assistant.gmail.exception.GmailMessageNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Acceso a Gmail de la cuenta autenticada usando el access token gestionado por Spring Security.
 * Los endpoints manuales son de solo lectura; la automatización además quita la etiqueta UNREAD (gmail.modify).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    public static final String GMAIL_MODIFY_SCOPE = "https://www.googleapis.com/auth/gmail.modify";

    private static final String USER_ID = "me";
    private static final String UNREAD_LABEL = "UNREAD";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private final GoogleTokenService tokenService;
    private final GmailMessageParser messageParser;
    private final GmailProperties properties;

    public List<GmailMessageDto> getUnreadMessages(Authentication authentication) {
        Gmail gmail = buildClient(tokenService.getAccessToken(authentication));
        List<String> ids = listMessageIds(gmail, properties.unreadQuery(), properties.maxResults());

        // messages.list solo devuelve id/threadId: se consulta cada mensaje completo.
        List<GmailMessageDto> messages = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                messages.add(messageParser.toDto(fetchMessage(gmail, id)));
            } catch (GmailMessageNotFoundException ex) {
                log.warn("El mensaje {} desapareció entre list y get; se omite", id);
            }
        }
        return messages;
    }

    public GmailMessageDto getMessage(Authentication authentication, String messageId) {
        return messageParser.toDto(fetchMessage(buildClient(tokenService.getAccessToken(authentication)), messageId));
    }

    // ---- Métodos con access token explícito, para procesos sin request HTTP (scheduler) ----

    /** IDs de los mensajes que cumplen la query de Gmail (ej. "in:inbox is:unread"). */
    public List<String> listMessageIds(String accessToken, String query, int maxResults) {
        return listMessageIds(buildClient(accessToken), query, maxResults);
    }

    /** Mensaje completo más su fecha real de recepción en Gmail (internalDate). */
    public ReceivedEmail getReceivedEmail(String accessToken, String messageId) {
        Message message = fetchMessage(buildClient(accessToken), messageId);
        Instant receivedAt = message.getInternalDate() != null ? Instant.ofEpochMilli(message.getInternalDate()) : null;
        return new ReceivedEmail(messageParser.toDto(message), receivedAt);
    }

    /** Quita la etiqueta UNREAD (users.messages.modify). Requiere el scope gmail.modify. */
    public void markAsRead(String accessToken, String messageId) {
        try {
            buildClient(accessToken).users().messages()
                    .modify(USER_ID, messageId, new ModifyMessageRequest().setRemoveLabelIds(List.of(UNREAD_LABEL)))
                    .execute();
        } catch (IOException ex) {
            throw translate(ex, messageId);
        }
    }

    private List<String> listMessageIds(Gmail gmail, String query, int maxResults) {
        ListMessagesResponse response;
        try {
            response = gmail.users().messages().list(USER_ID)
                    .setQ(query)
                    .setMaxResults((long) maxResults)
                    .execute();
        } catch (IOException ex) {
            throw translate(ex, null);
        }
        List<Message> references = response.getMessages();
        return references == null ? List.of() : references.stream().map(Message::getId).toList();
    }

    private Message fetchMessage(Gmail gmail, String messageId) {
        try {
            return gmail.users().messages().get(USER_ID, messageId)
                    .setFormat("full")
                    .execute();
        } catch (IOException ex) {
            throw translate(ex, messageId);
        }
    }

    private Gmail buildClient(String accessToken) {
        HttpRequestInitializer initializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
        return new Gmail.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), initializer)
                .setApplicationName(properties.applicationName())
                .build();
    }

    private RuntimeException translate(IOException ex, String messageId) {
        if (ex instanceof GoogleJsonResponseException responseException) {
            int status = responseException.getStatusCode();
            String detail = responseException.getDetails() != null
                    ? responseException.getDetails().getMessage()
                    : responseException.getStatusMessage();
            log.warn("Gmail API respondió {} (messageId={}): {}", status, messageId, detail);

            if (messageId != null && (status == 404 || status == 400)) {
                return new GmailMessageNotFoundException("No existe el mensaje con id " + messageId + ".");
            }
            if (status == 401) {
                return new GmailAuthorizationException("Google rechazó el access token. Vuelve a iniciar sesión.", ex);
            }
            if (status == 403 && String.valueOf(detail).toLowerCase().contains("insufficient")) {
                return new GoogleScopeMissingException("El token no tiene permiso suficiente para Gmail. "
                        + "Vuelve a autorizar la aplicación en /oauth2/authorization/google.", ex);
            }
            return new GmailApiException("Gmail API respondió con estado " + status + ": " + detail, ex);
        }
        return new GmailApiException("No se pudo comunicar con Gmail API.", ex);
    }
}
