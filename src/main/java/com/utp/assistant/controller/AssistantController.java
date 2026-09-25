package com.utp.assistant.controller;

import com.utp.assistant.config.OpenApiConfig;
import com.utp.assistant.dto.AnalyzeEmailRequest;
import com.utp.assistant.dto.GeminiEmailAnalysisResponse;
import com.utp.assistant.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ASSISTANT)
public class AssistantController {

    private static final String EXAMPLE_EMAIL = """
            {
              "messageId": "1a0d9a1269fc854a",
              "threadId": "1a0d9a1269fc854a",
              "from": "Luis Enrique Martínez Quijandria <lmartinezquijandria@gmail.com>",
              "subject": "Reunión módulo de pagos",
              "date": "Fri, 25 Sep 2026 12:33:19 -0500",
              "body": "Hola equipo de UTP Consult, Somos TechCorp y estamos interesados en continuar con la propuesta del módulo de pagos. ¿Podemos reunirnos el lunes a las 3:00 p.m.? Saludos, Ana Torres."
            }""";

    private final GeminiService geminiService;

    /** Devuelve los function calls que Gemini solicita para el correo. No ejecuta ninguno. */
    @Operation(summary = "Analizar correo con IA",
            description = "Gemini analiza el correo y decide qué acciones corresponden (CRM, Jira, Calendar) mediante "
                    + "Function Calling. Solo devuelve las acciones propuestas: no ejecuta ninguna.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AnalyzeEmailRequest.class),
                    examples = @ExampleObject(name = "Solicitud de reunión", value = EXAMPLE_EMAIL))))
    @ApiResponse(responseCode = "200", description = "Resumen y acciones propuestas por Gemini.")
    @ApiResponse(responseCode = "400", description = "Datos del correo inválidos.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Cuota de Gemini excedida.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Gemini no configurado o modelos saturados.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/analyze-email")
    public GeminiEmailAnalysisResponse analyzeEmail(@Valid @RequestBody AnalyzeEmailRequest request) {
        return geminiService.analyzeEmail(request);
    }
}
