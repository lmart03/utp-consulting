package com.utp.assistant.service;

import com.utp.assistant.exception.GmailAuthorizationException;
import com.utp.assistant.exception.GoogleScopeMissingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

/**
 * Obtiene el OAuth2AuthorizedClient de Google (access token + refresh token) del usuario autenticado.
 * Nunca registra el valor de los tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenService {

    static final String REGISTRATION_ID = "google";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public OAuth2AuthorizedClient getAuthorizedClient(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new GmailAuthorizationException("Usuario no autenticado.");
        }

        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(authentication)
                .build();

        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(request);
        } catch (ClientAuthorizationException ex) {
            log.warn("No se pudo renovar el access token de Google para '{}': {}",
                    authentication.getName(), ex.getError().getErrorCode());
            throw new GmailAuthorizationException(
                    "La autorización de Google expiró o fue revocada. Vuelve a iniciar sesión.", ex);
        }

        if (client == null || client.getAccessToken() == null) {
            throw new GmailAuthorizationException(
                    "No existe autorización de Google para el usuario. Inicia sesión en /oauth2/authorization/google.");
        }

        log.debug("Access token de Google disponible para '{}' (expira: {}, refresh token: {})",
                authentication.getName(), client.getAccessToken().getExpiresAt(), client.getRefreshToken() != null);
        return client;
    }

    public String getAccessToken(Authentication authentication) {
        return getAuthorizedClient(authentication).getAccessToken().getTokenValue();
    }

    /**
     * Access token que además incluye el scope indicado. Si el usuario autorizó la app antes de que se
     * agregara ese scope, debe volver a autorizarla.
     */
    public String getAccessTokenWithScope(Authentication authentication, String requiredScope) {
        OAuth2AccessToken accessToken = getAuthorizedClient(authentication).getAccessToken();
        if (!accessToken.getScopes().isEmpty() && !accessToken.getScopes().contains(requiredScope)) {
            throw new GoogleScopeMissingException("El token de Google no incluye el permiso " + requiredScope
                    + ". Vuelve a autorizar la aplicación en /oauth2/authorization/google.");
        }
        return accessToken.getTokenValue();
    }

    public boolean hasRefreshToken(Authentication authentication) {
        return getAuthorizedClient(authentication).getRefreshToken() != null;
    }
}
