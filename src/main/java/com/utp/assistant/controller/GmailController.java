package com.utp.assistant.controller;

import java.util.List;

import com.utp.assistant.config.OpenApiConfig;
import com.utp.assistant.dto.GmailMessageDto;
import com.utp.assistant.service.GmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_GMAIL)
@SecurityRequirement(name = OpenApiConfig.GOOGLE_SESSION)
@ApiResponse(responseCode = "401", description = "Sin sesión de Google o autorización expirada.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "502", description = "Error de Gmail API o contenido del correo no procesable.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class GmailController {

    private final GmailService gmailService;

    @Operation(summary = "Listar correos no leídos",
            description = "Obtiene hasta 10 mensajes no leídos desde Gmail de la cuenta autenticada. No los marca como leídos.")
    @ApiResponse(responseCode = "200", description = "Lista de correos (vacía si no hay no leídos).")
    @GetMapping("/unread")
    public List<GmailMessageDto> getUnread(Authentication authentication) {
        return gmailService.getUnreadMessages(authentication);
    }

    @Operation(summary = "Obtener un correo por ID",
            description = "Devuelve remitente, asunto, fecha y cuerpo en texto de un mensaje de Gmail.")
    @ApiResponse(responseCode = "200", description = "Correo encontrado.")
    @ApiResponse(responseCode = "400", description = "ID con formato inválido.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El mensaje no existe.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/messages/{id}")
    public GmailMessageDto getMessage(@Parameter(description = "ID del mensaje de Gmail.", example = "1a0d9a1269fc854a")
                                      @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
                                      Authentication authentication) {
        return gmailService.getMessage(authentication, id);
    }
}
