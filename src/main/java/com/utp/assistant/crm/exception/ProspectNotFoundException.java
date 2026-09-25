package com.utp.assistant.crm.exception;

/** No existe el prospecto (HTTP 404). */
public class ProspectNotFoundException extends RuntimeException {

    public ProspectNotFoundException(String message) {
        super(message);
    }
}
