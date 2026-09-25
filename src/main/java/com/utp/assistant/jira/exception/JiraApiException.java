package com.utp.assistant.jira.exception;

import org.springframework.http.HttpStatus;

/** Error devuelto por Jira o de comunicación con Jira. Lleva el estado HTTP a devolver al cliente. */
public class JiraApiException extends RuntimeException {

    private final HttpStatus status;

    public JiraApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public JiraApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
