package com.utp.assistant.service;

import java.time.Instant;
import java.util.Set;

import com.utp.assistant.exception.GmailAuthorizationException;
import com.utp.assistant.exception.GoogleScopeMissingException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenServiceTest {

    private static final String GMAIL = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String CALENDAR = "https://www.googleapis.com/auth/calendar.events";

    private final Authentication user = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

    @Test
    void returnsTokenWhenScopeWasGranted() {
        GoogleTokenService service = serviceWithScopes(GMAIL, CALENDAR);

        assertThat(service.getAccessTokenWithScope(user, CALENDAR)).isEqualTo("token-value");
    }

    @Test
    void failsWhenScopeWasNotGranted() {
        GoogleTokenService service = serviceWithScopes(GMAIL);

        assertThatThrownBy(() -> service.getAccessTokenWithScope(user, CALENDAR))
                .isInstanceOf(GoogleScopeMissingException.class)
                .hasMessageContaining("/oauth2/authorization/google");
    }

    @Test
    void failsWhenThereIsNoAuthorizedClient() {
        GoogleTokenService service = new GoogleTokenService(request -> null);

        assertThatThrownBy(() -> service.getAccessTokenWithScope(user, CALENDAR))
                .isInstanceOf(GmailAuthorizationException.class);
    }

    private static GoogleTokenService serviceWithScopes(String... scopes) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(3600), Set.of(scopes));
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(registration, "user", token);
        return new GoogleTokenService(request -> client);
    }
}
