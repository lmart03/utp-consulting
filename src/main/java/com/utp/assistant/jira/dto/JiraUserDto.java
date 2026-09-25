package com.utp.assistant.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cuenta Jira asociada a JIRA_EMAIL + JIRA_API_TOKEN (sin datos sensibles).")
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraUserDto(
        @Schema(example = "712020:abcd1234-...") String accountId,
        @Schema(example = "Luis Martínez") String displayName,
        @Schema(example = "lmartinezquijandria@gmail.com") String emailAddress) {
}
