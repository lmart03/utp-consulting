package com.utp.assistant.reply.exception;

/** La operación no aplica al estado actual de la respuesta, p. ej. enviar una ya enviada (HTTP 409). */
public class InvalidReplyStateException extends RuntimeException {

    public InvalidReplyStateException(String message) {
        super(message);
    }
}
