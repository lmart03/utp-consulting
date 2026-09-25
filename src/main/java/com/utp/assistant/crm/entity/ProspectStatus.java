package com.utp.assistant.crm.entity;

/**
 * Estado comercial del prospecto. El orden (rank) define el avance: un estado nunca retrocede automáticamente.
 * DISCARDED es especial: solo se aplica si se indica explícitamente, y un nuevo interés lo reabre.
 */
public enum ProspectStatus {
    NEW(0),
    CONTACTED(1),
    INTERESTED(2),
    MEETING_SCHEDULED(3),
    CLIENT(4),
    DISCARDED(-1);

    private final int rank;

    ProspectStatus(int rank) {
        this.rank = rank;
    }

    /** Estado resultante al combinar el actual con el solicitado por un nuevo correo. */
    public static ProspectStatus merge(ProspectStatus current, ProspectStatus requested) {
        if (requested == null) {
            return current == null ? NEW : current;
        }
        if (current == null || requested == DISCARDED || current == DISCARDED) {
            return requested;
        }
        return requested.rank > current.rank ? requested : current;
    }

    public static ProspectStatus parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
