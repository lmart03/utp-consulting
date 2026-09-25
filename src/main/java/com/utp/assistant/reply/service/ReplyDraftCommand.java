package com.utp.assistant.reply.service;

import java.util.List;

import com.utp.assistant.gmail.dto.GmailMessageDto;

/**
 * Datos para generar el borrador de un correo ya procesado.
 *
 * @param facts         acciones realizadas, en frases con datos exactos ("Número de seguimiento: SCRUM-14")
 * @param message       correo original si ya se leyó en este ciclo; null para que se lea de Gmail
 * @param principalName cuenta Google de la automatización (para leer el correo si hace falta)
 */
public record ReplyDraftCommand(Long processedEmailId, String gmailMessageId, String threadId, String from,
                                String subject, String summary, List<String> facts, GmailMessageDto message,
                                String principalName) {

    public ReplyDraftCommand {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
