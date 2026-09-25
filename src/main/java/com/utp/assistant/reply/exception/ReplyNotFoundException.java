package com.utp.assistant.reply.exception;

/** No existe la respuesta (HTTP 404). */
public class ReplyNotFoundException extends RuntimeException {

    public ReplyNotFoundException(String message) {
        super(message);
    }
}
