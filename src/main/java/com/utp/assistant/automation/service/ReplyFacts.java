package com.utp.assistant.automation.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.utp.assistant.automation.dto.AutomationDtos.ProcessedEmailDto;

/**
 * Convierte las acciones realizadas en frases con datos exactos para que Gemini las confirme en la respuesta
 * sin inventar nada (número de seguimiento, fecha y hora de la reunión en hora de negocio).
 */
final class ReplyFacts {

    private static final Locale SPANISH = Locale.forLanguageTag("es");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h:mm a", SPANISH);

    private ReplyFacts() {
    }

    static List<String> from(ProcessedEmailDto email, ZoneId zone) {
        List<String> facts = new ArrayList<>();
        if (email.crmContactName() != null) {
            facts.add("Nombre del contacto: " + email.crmContactName());
        }
        if (email.detectedCompany() != null) {
            facts.add("Empresa del contacto: " + email.detectedCompany());
        }
        if (email.jiraIssueKey() != null) {
            facts.add("La solicitud quedó registrada con el número de seguimiento " + email.jiraIssueKey());
        }
        if (email.meetingStart() != null) {
            facts.add("Reunión agendada: " + meeting(email.meetingStart(), email.meetingEnd(), zone));
        }
        return facts;
    }

    static String meeting(String start, String end, ZoneId zone) {
        try {
            var from = OffsetDateTime.parse(start).atZoneSameInstant(zone);
            String text = from.format(DAY) + ", de " + from.format(HOUR);
            if (end != null) {
                text += " a " + OffsetDateTime.parse(end).atZoneSameInstant(zone).format(HOUR);
            }
            return text + " (hora de " + cityOf(zone) + ")";
        } catch (DateTimeParseException ex) {
            return end == null ? start : start + " – " + end;
        }
    }

    /** "America/Lima" → "Lima" */
    private static String cityOf(ZoneId zone) {
        String id = zone.getId();
        return id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
    }
}
