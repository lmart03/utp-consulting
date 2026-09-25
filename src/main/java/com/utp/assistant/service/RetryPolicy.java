package com.utp.assistant.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.utp.assistant.config.AssistantProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backoff de reintentos: tras el intento N fallido se espera retryDelays[N-1] (1m, 5m, 15m).
 * Cuando el número de intentos supera 1 + maxRetries ya no se reintenta.
 */
@Component
public class RetryPolicy {

    private final int maxRetries;
    private final List<Duration> delays;

    @Autowired
    public RetryPolicy(AssistantProperties properties) {
        this(properties.processing().maxRetries(), properties.processing().retryDelays());
    }

    RetryPolicy(int maxRetries, List<Duration> delays) {
        if (delays == null || delays.isEmpty()) {
            throw new IllegalArgumentException("assistant.processing.retry-delays no puede estar vacío");
        }
        this.maxRetries = maxRetries;
        this.delays = List.copyOf(delays);
    }

    /** Próximo reintento tras el intento número {@code attemptCount} (1 = primer intento), o vacío si se agotaron. */
    public Optional<OffsetDateTime> nextRetryAt(int attemptCount, OffsetDateTime now) {
        if (attemptCount > maxRetries) {
            return Optional.empty();
        }
        Duration delay = delays.get(Math.min(Math.max(attemptCount, 1), delays.size()) - 1);
        return Optional.of(now.plus(delay));
    }

    public int maxRetries() {
        return maxRetries;
    }
}
