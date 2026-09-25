package com.utp.assistant.controller;

import java.util.List;

import com.utp.assistant.config.OpenApiConfig;
import com.utp.assistant.dto.JiraIssueRequest;
import com.utp.assistant.dto.JiraIssueResponse;
import com.utp.assistant.dto.JiraIssueTypeDto;
import com.utp.assistant.dto.JiraUserDto;
import com.utp.assistant.service.JiraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jira")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_JIRA)
@ApiResponse(responseCode = "401", description = "Credenciales Jira inválidas.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "503", description = "Jira no configurado (faltan variables JIRA_*).",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class JiraController {

    private static final String EXAMPLE_ISSUE = """
            {
              "summary": "Revisión requisitos técnicos - TechCorp",
              "description": "Revisión de requisitos técnicos para el módulo de pagos de TechCorp."
            }""";

    private final JiraService jiraService;

    @Operation(summary = "Verificar conexión con Jira",
            description = "Llama a GET /rest/api/3/myself para comprobar que JIRA_EMAIL y JIRA_API_TOKEN funcionan.")
    @ApiResponse(responseCode = "200", description = "Autenticación Jira correcta.")
    @GetMapping("/myself")
    public JiraUserDto myself() {
        return jiraService.getCurrentUser();
    }

    @Operation(summary = "Listar tipos de issue del proyecto",
            description = "Tipos de issue disponibles en el proyecto configurado (JIRA_PROJECT_KEY), para verificar que existe 'Task'.")
    @ApiResponse(responseCode = "200", description = "Tipos de issue del proyecto.")
    @ApiResponse(responseCode = "404", description = "Proyecto no encontrado o sin permiso para verlo.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/issue-types")
    public List<JiraIssueTypeDto> issueTypes() {
        return jiraService.getIssueTypes();
    }

    @Operation(summary = "Crear issue en Jira",
            description = "Crea una issue real (tipo JIRA_ISSUE_TYPE, por defecto Task) en el proyecto configurado. "
                    + "La descripción se envía en Atlassian Document Format.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JiraIssueRequest.class),
                    examples = @ExampleObject(name = "Tarea de seguimiento", value = EXAMPLE_ISSUE))))
    @ApiResponse(responseCode = "201", description = "Issue creada.")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, issue type o prioridad no reconocidos por Jira.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "La cuenta Jira no tiene permiso para crear issues en el proyecto.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public JiraIssueResponse createIssue(@Valid @RequestBody JiraIssueRequest request) {
        return jiraService.createIssue(request);
    }
}
