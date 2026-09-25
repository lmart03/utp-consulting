package com.utp.assistant.service;

import java.io.IOException;
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
import com.utp.assistant.config.GmailProperties;
import com.utp.assistant.dto.GmailMessageDto;
import com.utp.assistant.exception.GmailApiException;
import com.utp.assistant.exception.GmailAuthorizationException;
import com.utp.assistant.exception.GmailMessageNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Lectura (solo lectura) de Gmail de la cuenta autenticada usando el access token gestionado por Spring Security.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private static final String USER_ID = "me";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private final GoogleTokenService tokenService;
    private final GmailMessageParser messageParser;
    private final GmailProperties properties;

    public List<GmailMessageDto> getUnreadMessages(Authentication authentication) {
        Gmail gmail = buildClient(authentication);
        ListMessagesResponse response;
        try {
            response = gmail.users().messages().list(USER_ID)
                    .setQ(properties.unreadQuery())
                    .setMaxResults((long) properties.maxResults())
                    .execute();
        } catch (IOException ex) {
            throw translate(ex, null);
        }

        // messages.list solo devuelve id/threadId: se consulta cada mensaje completo.
        List<Message> references = response.getMessages();
        if (references == null || references.isEmpty()) {
            return List.of();
        }

        List<GmailMessageDto> messages = new ArrayList<>(references.size());
        for (Message reference : references) {
            try {
                messages.add(fetchMessage(gmail, reference.getId()));
            } catch (GmailMessageNotFoundException ex) {
                log.warn("El mensaje {} desapareció entre list y get; se omite", reference.getId());
            }
        }
        return messages;
    }

    public GmailMessageDto getMessage(Authentication authentication, String messageId) {
        return fetchMessage(buildClient(authentication), messageId);
    }

    private GmailMessageDto fetchMessage(Gmail gmail, String messageId) {
        try {
            Message message = gmail.users().messages().get(USER_ID, messageId)
                    .setFormat("full")
                    .execute();
            return messageParser.toDto(message);
        } catch (IOException ex) {
            throw translate(ex, messageId);
        }
    }

    private Gmail buildClient(Authentication authentication) {
        String accessToken = tokenService.getAccessToken(authentication);
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
            return new GmailApiException("Gmail API respondió con estado " + status + ": " + detail, ex);
        }
        return new GmailApiException("No se pudo comunicar con Gmail API.", ex);
    }
}
