package com.utp.assistant.automation.service;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyFactsTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void formatsMeetingInBusinessTimeZoneInSpanish() {
        String text = ReplyFacts.meeting("2026-09-28T20:00:00Z", "2026-09-28T21:00:00Z", LIMA);

        assertThat(text).startsWith("lunes 28 de septiembre de 2026, de 3:00");
        assertThat(text).contains(" a 4:00");
        assertThat(text).endsWith("(hora de Lima)");
    }

    @Test
    void keepsRawValueWhenDateCannotBeParsed() {
        assertThat(ReplyFacts.meeting("mañana", null, LIMA)).isEqualTo("mañana");
    }
}
