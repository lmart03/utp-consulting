package com.utp.assistant.exception;

/** El token de Google no tiene el scope necesario; hay que volver a autorizar la app (HTTP 403). */
public class GoogleScopeMissingException extends RuntimeException {

    public GoogleScopeMissingException(String message) {
        super(message);
    }

    public GoogleScopeMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
