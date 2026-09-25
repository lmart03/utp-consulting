package com.utp.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issue creada en Jira.")
public record JiraIssueResponse(
        @Schema(example = "10023") String id,
        @Schema(example = "SCRUM-3") String key,
        @Schema(description = "URL REST de la issue.", example = "https://utp-tics.atlassian.net/rest/api/3/issue/10023") String self,
        @Schema(description = "Enlace para abrir la issue en Jira.", example = "https://utp-tics.atlassian.net/browse/SCRUM-3")
        String browseUrl,
        @Schema(example = "Revisión requisitos técnicos - TechCorp") String summary) {
}
