package com.utp.assistant.entity;

public enum ProcessedEmailStatus {
    /** Correo reclamado y en procesamiento (con lease en nextRetryAt por si el proceso se cae). */
    PROCESSING,
    /** Todas las acciones requeridas terminaron correctamente (SUCCESS o SKIPPED). */
    PROCESSED,
    /** Gemini respondió, pero una o más herramientas fallaron; se reintenta en nextRetryAt. */
    PARTIAL,
    /** Falló (Gemini/Gmail). Con nextRetryAt se reintentará; sin nextRetryAt es definitivo (máximo de reintentos). */
    FAILED,
    /** Gemini determinó que el correo no requiere acciones. */
    IGNORED
}
