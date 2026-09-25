package com.utp.assistant.entity;

public enum EmailActionStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    /** No se ejecuta: herramienta aún no implementada (CRM) o fuera de la allowlist. No bloquea el flujo. */
    SKIPPED
}
