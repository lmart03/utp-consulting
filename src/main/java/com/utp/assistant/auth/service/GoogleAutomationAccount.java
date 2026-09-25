package com.utp.assistant.auth.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Cuenta Google que usa la automatización (demo de un solo usuario). Se registra al completar el login OAuth;
 * el scheduler la usa para pedir el OAuth2AuthorizedClient por principalName, sin HttpSession.
 * <p>
 * Se guarda en memoria igual que los tokens (InMemoryOAuth2AuthorizedClientService): tras reiniciar el backend
 * hay que volver a iniciar sesión con Google.
 */
@Slf4j
@Component
public class GoogleAutomationAccount {

    public record Account(String principalName, String email, OffsetDateTime registeredAt) {
    }

    private final AtomicReference<Account> current = new AtomicReference<>();

    public void register(Authentication authentication) {
        String email = authentication.getPrincipal() instanceof OAuth2User user ? user.getAttribute("email") : null;
        current.set(new Account(authentication.getName(), email, OffsetDateTime.now(ZoneOffset.UTC)));
        log.info("Cuenta Google registrada para la automatización: {}", email);
    }

    public Optional<Account> current() {
        return Optional.ofNullable(current.get());
    }

    /** Registra directamente una cuenta (usado por los tests de automatización). */
    public void register(Account account) {
        current.set(account);
    }
}
