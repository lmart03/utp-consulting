package com.utp.assistant.assistant.exception;

/** Falta GEMINI_API_KEY (HTTP 503). */
public class GeminiNotConfiguredException extends RuntimeException {

    public GeminiNotConfiguredException(String message) {
        super(message);
    }
}
