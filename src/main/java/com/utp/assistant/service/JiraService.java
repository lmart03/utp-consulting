package com.utp.assistant.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.utp.assistant.config.JiraProperties;
import com.utp.assistant.dto.JiraIssueRequest;
import com.utp.assistant.dto.JiraIssueResponse;
import com.utp.assistant.dto.JiraIssueTypeDto;
import com.utp.assistant.dto.JiraUserDto;
import com.utp.assistant.exception.JiraApiException;
import com.utp.assistant.exception.JiraNotConfiguredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integración manual con Jira Cloud REST API v3 (Basic Auth: email + API token, ver JiraConfig).
 */
@Slf4j
@Service
public class JiraService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_LOGGED_BODY = 2000;

    private final RestClient jiraRestClient;
    private final JiraProperties properties;

    public JiraService(RestClient jiraRestClient, JiraProperties properties) {
        this.jiraRestClient = jiraRestClient;
        this.properties = properties;
    }

    /** GET /rest/api/3/myself: verifica que JIRA_EMAIL + JIRA_API_TOKEN funcionan. */
    public JiraUserDto getCurrentUser() {
        requireConfigured();
        try {
            return jiraRestClient.get().uri("/rest/api/3/myself").retrieve().body(JiraUserDto.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex, "consultar la cuenta Jira");
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
    }

    /** Tipos de issue del proyecto configurado (GET /rest/api/3/project/{key}). */
    public List<JiraIssueTypeDto> getIssueTypes() {
        requireConfigured();
        try {
            JiraProject project = jiraRestClient.get()
                    .uri("/rest/api/3/project/{projectKey}", properties.projectKey())
                    .retrieve()
                    .body(JiraProject.class);
            return project == null || project.issueTypes() == null ? List.of() : project.issueTypes();
        } catch (RestClientResponseException ex) {
            throw translate(ex, "consultar el proyecto " + properties.projectKey());
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
    }

    /** POST /rest/api/3/issue con description en Atlassian Document Format. */
    public JiraIssueResponse createIssue(JiraIssueRequest request) {
        requireConfigured();
        JiraCreatedIssue created;
        try {
            created = jiraRestClient.post()
                    .uri("/rest/api/3/issue")
                    .body(buildIssuePayload(request))
                    .retrieve()
                    .body(JiraCreatedIssue.class);
        } catch (RestClientResponseException ex) {
            throw translateCreateError(ex, request);
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
        if (created == null || created.key() == null) {
            throw new JiraApiException(HttpStatus.BAD_GATEWAY, "Jira devolvió una respuesta inválida al crear la issue.");
        }

        log.info("Issue creada en Jira: {}", created.key());
        return new JiraIssueResponse(created.id(), created.key(), created.self(), browseUrl(created.key()), request.summary());
    }

    /** Payload de creación: project, summary, description (ADF), issuetype y priority solo si viene informada. */
    Map<String, Object> buildIssuePayload(JiraIssueRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", properties.projectKey()));
        fields.put("summary", request.summary().strip());
        fields.put("description", AdfDocumentBuilder.fromPlainText(request.description()));
        fields.put("issuetype", Map.of("name", properties.issueType()));
        if (request.priority() != null && !request.priority().isBlank()) {
            fields.put("priority", Map.of("name", request.priority().strip()));
        }
        return Map.of("fields", fields);
    }

    String browseUrl(String issueKey) {
        return properties.normalizedBaseUrl() + "/browse/" + issueKey;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new JiraNotConfiguredException(
                    "Jira no está configurado: define JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN y JIRA_PROJECT_KEY.");
        }
    }

    private JiraApiException translateCreateError(RestClientResponseException ex, JiraIssueRequest request) {
        JiraErrorBody body = parseErrorBody(ex);
        if (ex.getStatusCode().value() == 400 && body.errors().containsKey("issuetype")) {
            log.warn("Jira rechazó el issue type '{}': {}", properties.issueType(), abbreviate(ex.getResponseBodyAsString()));
            return new JiraApiException(HttpStatus.BAD_REQUEST, "El tipo de issue '" + properties.issueType()
                    + "' no es válido en el proyecto " + properties.projectKey() + ". Tipos disponibles: "
                    + availableIssueTypeNames() + ". Configura JIRA_ISSUE_TYPE.", ex);
        }
        if (ex.getStatusCode().value() == 400 && body.errors().containsKey("priority")) {
            log.warn("Jira rechazó la prioridad '{}': {}", request.priority(), abbreviate(ex.getResponseBodyAsString()));
            return new JiraApiException(HttpStatus.BAD_REQUEST, "Jira no acepta la prioridad '" + request.priority()
                    + "': " + body.errors().get("priority"), ex);
        }
        return translate(ex, "crear issues en el proyecto " + properties.projectKey());
    }

    private JiraApiException translate(RestClientResponseException ex, String action) {
        int status = ex.getStatusCode().value();
        String jiraDetail = parseErrorBody(ex).describe();
        log.warn("Jira respondió {} al {}: {}", status, action, abbreviate(ex.getResponseBodyAsString()));

        return switch (status) {
            case 400 -> new JiraApiException(HttpStatus.BAD_REQUEST,
                    "Jira rechazó la solicitud: revisar campos requeridos o issue type. " + jiraDetail, ex);
            case 401 -> new JiraApiException(HttpStatus.UNAUTHORIZED,
                    "Credenciales Jira inválidas. Revisa JIRA_EMAIL y JIRA_API_TOKEN.", ex);
            case 403 -> new JiraApiException(HttpStatus.FORBIDDEN,
                    "La cuenta Jira no tiene permisos para " + action + ".", ex);
            case 404 -> new JiraApiException(HttpStatus.NOT_FOUND,
                    "Jira no encontró el recurso al " + action + ". Revisa JIRA_BASE_URL y JIRA_PROJECT_KEY. " + jiraDetail, ex);
            default -> new JiraApiException(HttpStatus.BAD_GATEWAY,
                    "Jira respondió con estado " + status + " al " + action + ". " + jiraDetail, ex);
        };
    }

    private JiraApiException unreachable(ResourceAccessException ex) {
        log.warn("No se pudo conectar con Jira ({}): {}", properties.normalizedBaseUrl(), ex.getMessage());
        return new JiraApiException(HttpStatus.BAD_GATEWAY,
                "No se pudo conectar con Jira. Revisa JIRA_BASE_URL y la conexión.", ex);
    }

    private String availableIssueTypeNames() {
        try {
            return getIssueTypes().stream().map(JiraIssueTypeDto::name).collect(Collectors.joining(", "));
        } catch (RuntimeException ex) {
            return "(no se pudieron consultar: " + ex.getMessage() + ")";
        }
    }

    private static JiraErrorBody parseErrorBody(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return JiraErrorBody.EMPTY;
        }
        try {
            JiraErrorBody parsed = JSON.readValue(body, JiraErrorBody.class);
            return parsed == null ? JiraErrorBody.EMPTY : parsed.withDefaults();
        } catch (JacksonException notJson) {
            return JiraErrorBody.EMPTY;
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraCreatedIssue(String id, String key, String self) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraProject(String key, List<JiraIssueTypeDto> issueTypes) {
    }

    /** Formato estándar de error de Jira: {"errorMessages": [...], "errors": {"campo": "mensaje"}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraErrorBody(List<String> errorMessages, Map<String, String> errors) {

        static final JiraErrorBody EMPTY = new JiraErrorBody(List.of(), Map.of());

        JiraErrorBody withDefaults() {
            return new JiraErrorBody(errorMessages == null ? List.of() : errorMessages, errors == null ? Map.of() : errors);
        }

        String describe() {
            List<String> parts = new ArrayList<>(errorMessages);
            errors.forEach((field, message) -> parts.add(field + ": " + message));
            return parts.isEmpty() ? "" : "Detalle: " + String.join("; ", parts);
        }
    }
}
