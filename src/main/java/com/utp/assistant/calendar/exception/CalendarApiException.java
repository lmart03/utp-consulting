package com.utp.assistant.calendar.exception;

/** Error llamando a Google Calendar API o respuesta inválida (HTTP 502). */
public class CalendarApiException extends RuntimeException {

    public CalendarApiException(String message) {
        super(message);
    }

    public CalendarApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
