package com.utp.assistant.exception;

/** Usuario no autenticado o sin autorización válida de Google (HTTP 401). */
public class GmailAuthorizationException extends RuntimeException {

    public GmailAuthorizationException(String message) {
        super(message);
    }

    public GmailAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
