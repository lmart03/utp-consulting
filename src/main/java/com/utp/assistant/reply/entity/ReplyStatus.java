package com.utp.assistant.reply.entity;

/**
 * DRAFT: listo para revisar. SENDING: envío en curso (bloquea un doble envío). SENT: enviado en el hilo de Gmail.
 * DISCARDED: descartado por el usuario. FAILED: Gemini no pudo generar el borrador (se puede regenerar).
 */
public enum ReplyStatus {
    DRAFT,
    SENDING,
    SENT,
    DISCARDED,
    FAILED
}
