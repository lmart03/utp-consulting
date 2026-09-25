package com.utp.assistant.gmail.service;

import java.time.Instant;

import com.utp.assistant.gmail.dto.GmailMessageDto;

/** Correo leído desde Gmail junto con su fecha real de recepción (internalDate), usada para el cutoff. */
public record ReceivedEmail(GmailMessageDto message, Instant receivedAt) {
}
