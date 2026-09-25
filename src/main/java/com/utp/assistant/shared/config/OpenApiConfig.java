package com.utp.assistant.shared.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String GOOGLE_SESSION = "googleSession";

    public static final String TAG_AUTH = "Authentication";
    public static final String TAG_GMAIL = "Gmail";
    public static final String TAG_ASSISTANT = "Assistant";
    public static final String TAG_CALENDAR = "Google Calendar";
    public static final String TAG_JIRA = "Jira";
    public static final String TAG_AUTOMATION = "Automation";
    public static final String TAG_CRM = "CRM";

    @Bean
    OpenAPI utpAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UTP Assistant API")
                        .version("1.0.0")
                        .description("""
                                API para automatización de correos, análisis con IA y ejecución de acciones en Gmail, \
                                Jira, Google Calendar y CRM.

                                **Cómo autenticarse:** abre [/oauth2/authorization/google](/oauth2/authorization/google) \
                                en este mismo navegador e inicia sesión con Google. La sesión (cookie `JSESSIONID`) se \
                                envía automáticamente desde Swagger UI.

                                **CSRF:** las operaciones POST protegidas requieren el header `X-CSRF-TOKEN`; obtén el \
                                valor con `GET /api/auth/csrf`.""")
                        .contact(new Contact().name("UTP Consult")))
                .components(new Components().addSecuritySchemes(GOOGLE_SESSION, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Sesión creada al iniciar sesión con Google OAuth 2.0.")))
                // Orden de los grupos en Swagger UI.
                .tags(List.of(
                        new Tag().name(TAG_AUTH).description("Sesión Google OAuth 2.0 y token CSRF."),
                        new Tag().name(TAG_GMAIL).description("Lectura de correos de la cuenta autenticada (solo lectura)."),
                        new Tag().name(TAG_ASSISTANT).description("Análisis de correos con Gemini y Function Calling."),
                        new Tag().name(TAG_CALENDAR).description("Creación de eventos en Google Calendar."),
                        new Tag().name(TAG_JIRA).description("Creación de tareas en Jira Cloud (REST API v3, API token)."),
                        new Tag().name(TAG_CRM).description("Contactos comerciales creados/actualizados desde los correos."),
                        new Tag().name(TAG_AUTOMATION).description(
                                "Flujo automático Gmail → Gemini → Jira/Calendar → marcar leído, con registro en BD.")));
    }
}
