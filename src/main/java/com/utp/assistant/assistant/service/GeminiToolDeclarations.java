package com.utp.assistant.assistant.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.Type;

/**
 * FunctionDeclarations enviadas a Gemini. Solo describen las herramientas: aquí no se ejecuta nada.
 */
final class GeminiToolDeclarations {

    private GeminiToolDeclarations() {
    }

    /**
     * Función interna (no es una acción de negocio): con Function Calling Gemini suele devolver solo llamadas
     * a funciones y ningún texto, así que el resumen se pide también como llamada estructurada.
     * Nunca se ejecuta ni se registra como EmailAction.
     */
    static final String SUMMARY_FUNCTION = "registrar_resumen";

    static Tool tool() {
        return Tool.builder()
                .functionDeclarations(registrarResumen(), actualizarContactoCrm(), crearTicketJira(), agendarReunionGoogleCalendar())
                .build();
    }

    private static FunctionDeclaration registrarResumen() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("summary", string("Resumen breve en español (máximo 3 oraciones): quién escribe, empresa, intención "
                + "y acciones pendientes. No inventes datos."));
        return FunctionDeclaration.builder()
                .name(SUMMARY_FUNCTION)
                .description("Registra el resumen del correo analizado. Llámala SIEMPRE exactamente una vez por correo, "
                        + "también cuando no corresponda ninguna otra herramienta.")
                .parameters(object(properties, "summary"))
                .build();
    }

    private static FunctionDeclaration actualizarContactoCrm() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("name", string("Nombre del contacto (firma del correo o, si no existe, nombre del header From)."));
        properties.put("email", string("Email real del remitente, tomado del header From."));
        properties.put("company", string("Empresa del contacto, solo si se menciona."));
        properties.put("phone", string("Teléfono del contacto, solo si se menciona."));
        properties.put("status", Schema.builder()
                .type(Type.Known.STRING)
                .description("Estado comercial del prospecto según el correo.")
                .enum_("NEW", "CONTACTED", "INTERESTED", "MEETING_SCHEDULED", "CLIENT", "DISCARDED")
                .build());
        properties.put("notes", string("Notas relevantes: intención, requerimientos, contexto comercial."));

        return FunctionDeclaration.builder()
                .name(AssistantTool.ACTUALIZAR_CONTACTO_CRM.functionName())
                .description("Crea o actualiza un prospecto en el CRM usando información obtenida del email.")
                .parameters(object(properties, "name", "email", "status"))
                .build();
    }

    private static FunctionDeclaration crearTicketJira() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("summary", string("Título breve de la tarea."));
        properties.put("description", string("Descripción del requerimiento o acción pendiente, con el contexto del correo."));
        properties.put("priority", string("Prioridad (Highest, High, Medium, Low, Lowest). Omitir si el correo no da razones suficientes."));

        return FunctionDeclaration.builder()
                .name(AssistantTool.CREAR_TICKET_JIRA.functionName())
                .description("Crea una tarea en Jira cuando el email contiene un requerimiento, acción pendiente, "
                        + "solicitud técnica o seguimiento.")
                .parameters(object(properties, "summary", "description"))
                .build();
    }

    private static FunctionDeclaration agendarReunionGoogleCalendar() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("title", string("Título de la reunión."));
        properties.put("description", string("Descripción o agenda de la reunión."));
        properties.put("startDateTime", string("Inicio en ISO-8601 con offset de America/Lima, ej. 2026-09-28T15:00:00-05:00."));
        properties.put("endDateTime", string("Fin en ISO-8601 con offset de America/Lima. Si no se indica duración, inicio + 60 minutos."));
        properties.put("attendeeEmail", string("Email del invitado: el remitente real (header From)."));

        return FunctionDeclaration.builder()
                .name(AssistantTool.AGENDAR_REUNION_GOOGLE_CALENDAR.functionName())
                .description("Crea una reunión cuando el correo contiene intención clara de reunión y fecha y hora suficientes.")
                .parameters(object(properties, "title", "startDateTime", "endDateTime", "attendeeEmail"))
                .build();
    }

    private static Schema string(String description) {
        return Schema.builder().type(Type.Known.STRING).description(description).build();
    }

    private static Schema object(Map<String, Schema> properties, String... required) {
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(properties)
                .required(required)
                .build();
    }
}
