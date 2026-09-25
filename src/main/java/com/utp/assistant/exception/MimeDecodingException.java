package com.utp.assistant.exception;

/** Error decodificando el contenido MIME de un mensaje (HTTP 502). */
public class MimeDecodingException extends RuntimeException {

    public MimeDecodingException(String message) {
        super(message);
    }

    public MimeDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
