package com.utp.assistant.automation.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 25, 12, 0, 0, 0, ZoneOffset.UTC);

    private final RetryPolicy policy = new RetryPolicy(3,
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)));

    @Test
    void backoffIsOneFiveAndFifteenMinutes() {
        assertThat(policy.nextRetryAt(1, NOW)).contains(NOW.plusMinutes(1));
        assertThat(policy.nextRetryAt(2, NOW)).contains(NOW.plusMinutes(5));
        assertThat(policy.nextRetryAt(3, NOW)).contains(NOW.plusMinutes(15));
    }

    @Test
    void noMoreRetriesAfterMaxRetries() {
        assertThat(policy.nextRetryAt(4, NOW)).isEmpty();
    }

    @Test
    void lastDelayIsReusedWhenThereAreMoreRetriesThanDelays() {
        RetryPolicy longer = new RetryPolicy(5, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)));

        assertThat(longer.nextRetryAt(4, NOW)).contains(NOW.plusMinutes(5));
        assertThat(longer.nextRetryAt(6, NOW)).isEmpty();
    }
}
