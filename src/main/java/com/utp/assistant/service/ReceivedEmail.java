package com.utp.assistant.service;

import java.time.Instant;

import com.utp.assistant.dto.GmailMessageDto;

/** Correo leído desde Gmail junto con su fecha real de recepción (internalDate), usada para el cutoff. */
public record ReceivedEmail(GmailMessageDto message, Instant receivedAt) {
}
