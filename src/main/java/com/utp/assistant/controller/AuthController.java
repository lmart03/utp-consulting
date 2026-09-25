package com.utp.assistant.controller;

import com.utp.assistant.config.OpenApiConfig;
import com.utp.assistant.dto.AuthUserDto;
import com.utp.assistant.service.GoogleTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTH)
@SecurityRequirement(name = OpenApiConfig.GOOGLE_SESSION)
@ApiResponse(responseCode = "401", description = "No hay sesión de Google activa.", content = @Content)
public class AuthController {

    private final GoogleTokenService tokenService;

    @Operation(summary = "Usuario autenticado",
            description = "Devuelve nombre y email de la cuenta Google con sesión activa e indica si hay refresh token.")
    @ApiResponse(responseCode = "200", description = "Sesión activa.")
    @GetMapping("/me")
    public AuthUserDto me(@AuthenticationPrincipal OAuth2User user, Authentication authentication) {
        return new AuthUserDto(
                true,
                user.getAttribute("name"),
                user.getAttribute("email"),
                tokenService.hasRefreshToken(authentication));
    }

    /**
     * Token CSRF de la sesión actual. Los POST/PUT/DELETE deben enviarlo en el header indicado (X-CSRF-TOKEN).
     */
    @Operation(summary = "Obtener token CSRF",
            description = "Token CSRF de la sesión actual. Envíalo en el header `X-CSRF-TOKEN` en las operaciones POST protegidas.")
    @ApiResponse(responseCode = "200", description = "Token CSRF de la sesión.", content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject("{\"headerName\":\"X-CSRF-TOKEN\",\"parameterName\":\"_csrf\",\"token\":\"b1c2...\"}")))
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }
}
