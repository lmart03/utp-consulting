package com.utp.assistant.crm.exception;

/** Datos del contacto inválidos (p. ej. sin email válido) (HTTP 400). */
public class InvalidProspectException extends RuntimeException {

    public InvalidProspectException(String message) {
        super(message);
    }
}
