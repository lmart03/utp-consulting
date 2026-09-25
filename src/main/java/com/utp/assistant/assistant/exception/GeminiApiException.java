package com.utp.assistant.assistant.exception;

import org.springframework.http.HttpStatus;

/** Error llamando a Gemini API o respuesta inválida. Lleva el estado HTTP a devolver al cliente. */
public class GeminiApiException extends RuntimeException {

    private final HttpStatus status;

    public GeminiApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public GeminiApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
