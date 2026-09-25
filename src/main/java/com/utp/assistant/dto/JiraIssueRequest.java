package com.utp.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Tarea a crear en Jira. La descripción se convierte a Atlassian Document Format.")
public record JiraIssueRequest(
        @Schema(example = "Revisión requisitos técnicos - TechCorp") @NotBlank @Size(max = 255) String summary,
        @Schema(description = "Texto plano; los saltos de línea se conservan.",
                example = "Revisión de requisitos técnicos para el módulo de pagos de TechCorp.")
        @NotBlank @Size(max = 30_000) String description,
        @Schema(description = "Opcional. Debe existir en Jira (ej. Highest, High, Medium, Low, Lowest).", example = "Medium")
        @Size(max = 50) String priority) {
}
