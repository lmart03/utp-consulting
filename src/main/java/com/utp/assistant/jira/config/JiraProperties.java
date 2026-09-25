package com.utp.assistant.jira.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jira")
public record JiraProperties(
        String baseUrl,
        String email,
        String apiToken,
        String projectKey,
        String issueType,
        Duration connectTimeout,
        Duration readTimeout) {

    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(email) && notBlank(apiToken) && notBlank(projectKey);
    }

    /** URL base sin "/" final, para construir enlaces /browse/{key}. */
    public String normalizedBaseUrl() {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    /** Evita que el API token aparezca si alguien imprime las propiedades. */
    @Override
    public String toString() {
        return "JiraProperties[baseUrl=" + baseUrl + ", projectKey=" + projectKey + ", issueType=" + issueType
                + ", email=" + (notBlank(email) ? "****" : "<vacío>")
                + ", apiToken=" + (notBlank(apiToken) ? "****" : "<vacío>") + "]";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
