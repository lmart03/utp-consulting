package com.utp.assistant.assistant.dto;

import java.util.List;

/**
 * Datos para redactar un borrador de respuesta.
 *
 * @param body      cuerpo del correo original (null si no se pudo leer: se redacta solo con el resumen)
 * @param facts     acciones ya realizadas con sus datos exactos (número de seguimiento, fecha de la reunión…)
 * @param signature firma con la que cierra la respuesta
 */
public record ReplyDraftRequest(String from, String subject, String date, String body, String summary,
                                List<String> facts, String signature) {

    public ReplyDraftRequest {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
