package com.utp.assistant.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración de la automatización end-to-end (prefijo "assistant"). */
@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(Polling polling, Automation automation, Processing processing) {

    /**
     * @param enabled si es false no se registra el scheduler (process-now sigue disponible)
     * @param delayMs pausa entre el fin de un ciclo y el inicio del siguiente (fixedDelay)
     */
    public record Polling(boolean enabled, long delayMs) {
    }

    /**
     * @param processOldEmails  si es false solo se procesan correos recibidos después de automationStartedAt
     * @param gmailQuery        búsqueda de candidatos en Gmail (UNREAD solo sirve para encontrar candidatos)
     * @param maxResults        máximo de correos candidatos por ciclo
     * @param processingLease   si un correo queda PROCESSING más de este tiempo (caída del proceso) se retoma
     */
    public record Automation(boolean processOldEmails, String gmailQuery, int maxResults, Duration processingLease) {
    }

    /**
     * @param maxRetries    reintentos después del primer intento fallido
     * @param retryDelays   espera antes de cada reintento (1m, 5m, 15m); si hay más reintentos se repite el último
     * @param markReadRetryDelay espera antes de reintentar solo el marcado como leído en Gmail
     */
    public record Processing(int maxRetries, List<Duration> retryDelays, Duration markReadRetryDelay) {
    }
}
