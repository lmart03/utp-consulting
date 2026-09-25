package com.utp.assistant.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de issue disponible en el proyecto Jira.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueTypeDto(
        @Schema(example = "10001") String id,
        @Schema(example = "Task") String name,
        @Schema(example = "A small, distinct piece of work.") String description,
        @Schema(description = "true si es una subtarea.", example = "false") boolean subtask) {
}
