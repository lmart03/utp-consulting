package com.utp.assistant.exception;

/** Error llamando a Gmail API o respuesta inválida (HTTP 502). */
public class GmailApiException extends RuntimeException {

    public GmailApiException(String message) {
        super(message);
    }

    public GmailApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
