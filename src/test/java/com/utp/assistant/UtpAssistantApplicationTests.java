package com.utp.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Requiere PostgreSQL local: docker compose up -d
// La API key de Gemini se vacía para que los tests nunca llamen a Gemini real.
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.gemini.api-key=",
        "app.jira.api-token="
})
@AutoConfigureMockMvc
class UtpAssistantApplicationTests {

    private static final String VALID_EMAIL = """
            {"messageId":"1","threadId":"1","from":"Ana <ana@techcorp.com>","subject":"Hola",
             "date":"Fri, 25 Sep 2026 12:22:05 -0500","body":"Hola equipo"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void apiRequiresAuthenticationAndReturns401() throws Exception {
        mockMvc.perform(get("/api/gmail/unread")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/calendar/events").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jiraEndpointsArePublicWithoutCsrfDuringDevelopment() throws Exception {
        // TEMPORAL (ver SecurityConfig). Sin credenciales Jira responde 503 en lugar de 401/403.
        mockMvc.perform(get("/api/jira/myself")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/jira/issues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"S\",\"description\":\"D\"}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/jira/issues").contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocsAndSwaggerUiArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("UTP Assistant API"),
                        containsString("/api/gmail/unread"),
                        containsString("/api/assistant/analyze-email"),
                        containsString("/api/calendar/events"))));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void calendarEventsSkipCsrfDuringDevelopmentButStillNeedGoogleAuthorization() throws Exception {
        // TEMPORAL: sin CSRF (ver SecurityConfig). Sin OAuth2AuthorizedClient de Google responde 401 igualmente.
        mockMvc.perform(post("/api/calendar/events").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calendarEventValidatesRequest() throws Exception {
        mockMvc.perform(post("/api/calendar/events").with(oauth2Login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"startDateTime\":\"x\",\"endDateTime\":\"y\",\"attendeeEmail\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest());
    }

    private static final String VALID_EVENT = """
            {"title":"Reunión","startDateTime":"2026-09-28T15:00:00-05:00",
             "endDateTime":"2026-09-28T16:00:00-05:00","attendeeEmail":"ana@techcorp.com"}
            """;

    @Test
    void authorizationRequestIncludesOfflineAccessAndConsent() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        containsString("access_type=offline"),
                        containsString("prompt=consent"),
                        containsString("gmail.readonly"),
                        containsString("redirect_uri=http://localhost/login/oauth2/code/google"))));
    }

    @Test
    void analyzeEmailIsPublicWithoutCsrfDuringDevelopment() throws Exception {
        // TEMPORAL: /api/assistant/** es público y sin CSRF para probar con Postman (ver SecurityConfig).
        mockMvc.perform(post("/api/assistant/analyze-email")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EMAIL))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void analyzeEmailValidatesRequest() throws Exception {
        mockMvc.perform(post("/api/assistant/analyze-email").with(oauth2Login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeEmailReturns503WhenGeminiIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/assistant/analyze-email").with(oauth2Login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EMAIL))
                .andExpect(status().isServiceUnavailable());
    }
}
