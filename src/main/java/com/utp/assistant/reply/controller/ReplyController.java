package com.utp.assistant.reply.controller;

import com.utp.assistant.reply.dto.EmailReplyDto;
import com.utp.assistant.reply.dto.SendReplyRequest;
import com.utp.assistant.reply.exception.ReplyNotFoundException;
import com.utp.assistant.reply.service.ReplyService;
import com.utp.assistant.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Revisión y envío de respuestas sugeridas. A diferencia de los endpoints de prueba, exige sesión de Google y
 * token CSRF: enviar un correo en nombre del usuario es una acción sensible.
 */
@RestController
@RequestMapping("/api/replies")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_REPLIES)
@SecurityRequirement(name = OpenApiConfig.GOOGLE_SESSION)
public class ReplyController {

    private final ReplyService replyService;

    @Operation(summary = "Respuesta sugerida de un correo",
            description = "Borrador de respuesta asociado a un correo procesado (id interno, no el de Gmail).")
    @ApiResponse(responseCode = "200", description = "Respuesta sugerida.")
    @ApiResponse(responseCode = "404", description = "El correo no tiene respuesta sugerida.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public EmailReplyDto byEmail(@Parameter(description = "Id interno del correo procesado.", example = "12")
                                 @RequestParam Long processedEmailId) {
        return replyService.findByProcessedEmailId(processedEmailId)
                .map(EmailReplyDto::from)
                .orElseThrow(() -> new ReplyNotFoundException("El correo " + processedEmailId + " no tiene respuesta sugerida."));
    }

    @Operation(summary = "Enviar respuesta",
            description = "Envía el texto aprobado (tal cual o editado) como respuesta en el mismo hilo de Gmail. "
                    + "Solo desde DRAFT; un segundo envío responde 409.")
    @ApiResponse(responseCode = "200", description = "Respuesta enviada.")
    @ApiResponse(responseCode = "409", description = "La respuesta ya fue enviada, descartada o se está enviando.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/send")
    public EmailReplyDto send(@PathVariable Long id, @Valid @RequestBody SendReplyRequest request,
                              Authentication authentication) {
        return EmailReplyDto.from(replyService.send(id, request.body(), authentication));
    }

    @Operation(summary = "Regenerar borrador", description = "Pide a Gemini un nuevo borrador (si falló o no convenció).")
    @ApiResponse(responseCode = "200", description = "Nuevo borrador (o FAILED con el motivo).")
    @PostMapping("/{id}/regenerate")
    public EmailReplyDto regenerate(@PathVariable Long id, Authentication authentication) {
        return EmailReplyDto.from(replyService.regenerate(id, authentication));
    }

    @Operation(summary = "Descartar borrador", description = "Marca la respuesta como descartada; no se envía nada.")
    @ApiResponse(responseCode = "200", description = "Respuesta descartada.")
    @PostMapping("/{id}/discard")
    public EmailReplyDto discard(@PathVariable Long id) {
        return EmailReplyDto.from(replyService.discard(id));
    }
}
