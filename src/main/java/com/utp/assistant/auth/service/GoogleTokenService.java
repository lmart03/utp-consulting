package com.utp.assistant.auth.service;

import com.utp.assistant.auth.exception.GmailAuthorizationException;
import com.utp.assistant.auth.exception.GoogleScopeMissingException;
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
 * Obtiene el OAuth2AuthorizedClient de Google (access token + refresh token), ya sea del usuario de la request
 * o, para procesos en segundo plano, por principalName (sin HttpSession ni SecurityContextHolder).
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
        return authorize(OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal(authentication)
                .build(), authentication.getName());
    }

    /** Para el scheduler: usa el OAuth2AuthorizedClientService con el principalName registrado al hacer login. */
    public OAuth2AuthorizedClient getAuthorizedClient(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new GmailAuthorizationException("No hay una cuenta Google registrada para la automatización.");
        }
        return authorize(OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal(principalName)
                .build(), principalName);
    }

    public String getAccessToken(Authentication authentication) {
        return getAuthorizedClient(authentication).getAccessToken().getTokenValue();
    }

    /**
     * Access token que además incluye el scope indicado. Si el usuario autorizó la app antes de que se
     * agregara ese scope, debe volver a autorizarla.
     */
    public String getAccessTokenWithScope(Authentication authentication, String requiredScope) {
        return requireScope(getAuthorizedClient(authentication).getAccessToken(), requiredScope);
    }

    public String getAccessTokenWithScope(String principalName, String requiredScope) {
        return requireScope(getAuthorizedClient(principalName).getAccessToken(), requiredScope);
    }

    public boolean hasRefreshToken(Authentication authentication) {
        return getAuthorizedClient(authentication).getRefreshToken() != null;
    }

    private OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request, String principalName) {
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(request);
        } catch (ClientAuthorizationException ex) {
            log.warn("No se pudo renovar el access token de Google para '{}': {}",
                    principalName, ex.getError().getErrorCode());
            throw new GmailAuthorizationException(
                    "La autorización de Google expiró o fue revocada. Vuelve a iniciar sesión.", ex);
        }

        if (client == null || client.getAccessToken() == null) {
            throw new GmailAuthorizationException(
                    "No existe autorización de Google para el usuario. Inicia sesión en /oauth2/authorization/google.");
        }

        log.debug("Access token de Google disponible para '{}' (expira: {}, refresh token: {})",
                principalName, client.getAccessToken().getExpiresAt(), client.getRefreshToken() != null);
        return client;
    }

    private static String requireScope(OAuth2AccessToken accessToken, String requiredScope) {
        if (!accessToken.getScopes().isEmpty() && !accessToken.getScopes().contains(requiredScope)) {
            throw new GoogleScopeMissingException("El token de Google no incluye el permiso " + requiredScope
                    + ". Vuelve a autorizar la aplicación en /oauth2/authorization/google.");
        }
        return accessToken.getTokenValue();
    }
}
