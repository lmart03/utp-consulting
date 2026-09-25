package com.utp.assistant.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OAuth2ClientConfig {

    /**
     * Recupera el OAuth2AuthorizedClient guardado tras el login y renueva el access token
     * con el refresh token cuando está por expirar.
     * <p>
     * Se basa en OAuth2AuthorizedClientService (no en la request HTTP), por lo que también servirá
     * para procesos en segundo plano. Hoy el service es el InMemory que autoconfigura Spring Boot;
     * para persistir el refresh token bastará con declarar un JdbcOAuth2AuthorizedClientService
     * (u otra implementación) como bean.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                          OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .refreshToken()
                .build());
        return manager;
    }
}
