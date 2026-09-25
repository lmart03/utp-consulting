package com.utp.assistant.exception;

/** Mensaje de Gmail inexistente (HTTP 404). */
public class GmailMessageNotFoundException extends RuntimeException {

    public GmailMessageNotFoundException(String message) {
        super(message);
    }

    public GmailMessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
