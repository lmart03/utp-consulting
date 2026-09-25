package com.utp.assistant.crm.service;

/**
 * Datos de un contacto provenientes de un correo (automatización).
 *
 * @param from             header From real del correo: fuente confiable del email
 * @param meetingRequested el mismo correo pidió agendar reunión → el prospecto pasa a MEETING_SCHEDULED
 * @param name,email,company,phone,status,notes argumentos de Gemini (pueden venir vacíos)
 */
public record CrmContactCommand(
        String from,
        String gmailMessageId,
        String subject,
        String name,
        String email,
        String company,
        String phone,
        String status,
        String notes,
        boolean meetingRequested) {
}
