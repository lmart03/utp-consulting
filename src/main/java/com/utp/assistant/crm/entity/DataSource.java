package com.utp.assistant.crm.entity;

/** De dónde salió un dato del prospecto (para distinguir datos reales de inferidos). */
public enum DataSource {
    /** Detectado por Gemini en el contenido del correo (p. ej. la firma). */
    GEMINI,
    /** Nombre visible del header From ("Ana Torres" &lt;ana@...&gt;). */
    FROM_HEADER,
    /** Derivado del usuario del email (ana.torres@ → "Ana Torres"). */
    EMAIL_ADDRESS,
    /** Derivado del dominio corporativo (@andinalogistics.com → "Andinalogistics"). */
    EMAIL_DOMAIN,
    /** Ingresado manualmente por la API. */
    MANUAL;

    /** Un dato inferido puede ser reemplazado por uno real; uno real no se pisa con uno inferido. */
    public boolean isInferred() {
        return this == FROM_HEADER || this == EMAIL_ADDRESS || this == EMAIL_DOMAIN;
    }
}
