package com.utp.assistant.calendar.exception;

/** Datos del evento inválidos: fechas mal formadas o fin anterior al inicio (HTTP 400). */
public class InvalidCalendarEventException extends RuntimeException {

    public InvalidCalendarEventException(String message) {
        super(message);
    }

    public InvalidCalendarEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
