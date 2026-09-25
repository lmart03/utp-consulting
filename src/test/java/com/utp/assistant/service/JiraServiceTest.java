package com.utp.assistant.service;

import java.time.Duration;
import java.util.Map;

import com.utp.assistant.config.JiraConfig;
import com.utp.assistant.config.JiraProperties;
import com.utp.assistant.dto.JiraIssueRequest;
import com.utp.assistant.dto.JiraIssueResponse;
import com.utp.assistant.exception.JiraApiException;
import com.utp.assistant.exception.JiraNotConfiguredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraServiceTest {

    private static final String BASE_URL = "https://utp-tics.atlassian.net";
    private static final JiraProperties PROPERTIES = new JiraProperties(BASE_URL + "/", "ana@utp.edu.pe", "abc123",
            "SCRUM", "Task", Duration.ofSeconds(5), Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private JiraService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", JiraConfig.basicAuthHeader(PROPERTIES.email(), PROPERTIES.apiToken()));
        server = MockRestServiceServer.bindTo(builder).build();
        service = new JiraService(builder.build(), PROPERTIES);
    }

    @Test
    void basicAuthHeaderIsBase64OfEmailColonToken() {
        assertThat(JiraConfig.basicAuthHeader(" ana@utp.edu.pe ", "abc123"))
                .isEqualTo("Basic YW5hQHV0cC5lZHUucGU6YWJjMTIz");
    }

    @Test
    void payloadUsesProjectIssueTypeAndAdfDescriptionWithoutPriorityWhenMissing() {
        Map<String, Object> payload = service.buildIssuePayload(
                new JiraIssueRequest("Revisión requisitos técnicos - TechCorp", "Revisión del módulo de pagos.", null));

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) payload.get("fields");
        assertThat(fields).containsEntry("project", Map.of("key", "SCRUM"))
                .containsEntry("summary", "Revisión requisitos técnicos - TechCorp")
                .containsEntry("issuetype", Map.of("name", "Task"))
                .containsEntry("description", AdfDocumentBuilder.fromPlainText("Revisión del módulo de pagos."))
                .doesNotContainKey("priority");
    }

    @Test
    void blankPriorityIsNotSentButInformedPriorityIs() {
        assertThat(fields(service.buildIssuePayload(new JiraIssueRequest("S", "D", "  ")))).doesNotContainKey("priority");
        assertThat(fields(service.buildIssuePayload(new JiraIssueRequest("S", "D", "High"))))
                .containsEntry("priority", Map.of("name", "High"));
    }

    @Test
    void browseUrlUsesBaseUrlWithoutTrailingSlash() {
        assertThat(service.browseUrl("SCRUM-3")).isEqualTo("https://utp-tics.atlassian.net/browse/SCRUM-3");
    }

    @Test
    void createsIssueWithBasicAuthAndMapsResponse() {
        server.expect(requestTo(BASE_URL + "/rest/api/3/issue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YW5hQHV0cC5lZHUucGU6YWJjMTIz"))
                .andExpect(content().json("""
                        {"fields":{"project":{"key":"SCRUM"},"summary":"Revisión requisitos técnicos - TechCorp",
                         "issuetype":{"name":"Task"},
                         "description":{"type":"doc","version":1,"content":[{"type":"paragraph","content":[
                           {"type":"text","text":"Revisión de requisitos técnicos para el módulo de pagos de TechCorp."}]}]}}}
                        """, true))
                .andRespond(withSuccess("""
                        {"id":"10023","key":"SCRUM-3","self":"https://utp-tics.atlassian.net/rest/api/3/issue/10023"}
                        """, MediaType.APPLICATION_JSON));

        JiraIssueResponse response = service.createIssue(new JiraIssueRequest("Revisión requisitos técnicos - TechCorp",
                "Revisión de requisitos técnicos para el módulo de pagos de TechCorp.", null));

        assertThat(response).isEqualTo(new JiraIssueResponse("10023", "SCRUM-3",
                "https://utp-tics.atlassian.net/rest/api/3/issue/10023",
                "https://utp-tics.atlassian.net/browse/SCRUM-3", "Revisión requisitos técnicos - TechCorp"));
        server.verify();
    }

    @Test
    void invalidIssueTypeErrorListsAvailableTypes() {
        server.expect(requestTo(BASE_URL + "/rest/api/3/issue"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessages\":[],\"errors\":{\"issuetype\":\"Specify a valid issue type\"}}"));
        server.expect(requestTo(BASE_URL + "/rest/api/3/project/SCRUM"))
                .andRespond(withSuccess("""
                        {"key":"SCRUM","issueTypes":[{"id":"1","name":"Tarea","subtask":false},{"id":"2","name":"Epic","subtask":false}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.createIssue(new JiraIssueRequest("S", "D", null)))
                .isInstanceOf(JiraApiException.class)
                .hasMessageContaining("'Task' no es válido")
                .hasMessageContaining("Tarea, Epic");
    }

    @Test
    void unauthorizedIsMappedToClearMessageWithoutToken() {
        server.expect(requestTo(BASE_URL + "/rest/api/3/myself")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(JiraApiException.class)
                .hasMessageContaining("Credenciales Jira inválidas")
                .hasMessageNotContaining("abc123")
                .satisfies(ex -> assertThat(((JiraApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void forbiddenMentionsProject() {
        server.expect(requestTo(BASE_URL + "/rest/api/3/issue")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.createIssue(new JiraIssueRequest("S", "D", null)))
                .hasMessageContaining("no tiene permisos para crear issues en el proyecto SCRUM");
    }

    @Test
    void missingConfigurationFailsBeforeCallingJira() {
        JiraService notConfigured = new JiraService(RestClient.create(),
                new JiraProperties("", "", "", "", "Task", Duration.ofSeconds(1), Duration.ofSeconds(1)));

        assertThatThrownBy(notConfigured::getCurrentUser).isInstanceOf(JiraNotConfiguredException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("fields");
    }
}
