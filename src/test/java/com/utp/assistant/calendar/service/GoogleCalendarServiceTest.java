package com.utp.assistant.calendar.service;

import java.time.ZoneId;

import com.google.api.services.calendar.model.Event;
import com.utp.assistant.calendar.config.CalendarProperties;
import com.utp.assistant.calendar.dto.CalendarEventRequest;
import com.utp.assistant.calendar.exception.InvalidCalendarEventException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCalendarServiceTest {

    private final GoogleCalendarService service = new GoogleCalendarService(
            null, new CalendarProperties("primary", ZoneId.of("America/Lima"), "none"), null);

    @Test
    void buildsEventWithLimaTimeZoneAndAttendee() {
        Event event = service.buildEvent(new CalendarEventRequest("Reunión módulo de pagos - TechCorp", "Detalle",
                "2026-09-28T15:00:00-05:00", "2026-09-28T16:00:00-05:00", "ana@techcorp.com"));

        assertThat(event.getSummary()).isEqualTo("Reunión módulo de pagos - TechCorp");
        assertThat(event.getDescription()).isEqualTo("Detalle");
        assertThat(event.getStart().getTimeZone()).isEqualTo("America/Lima");
        assertThat(event.getStart().getDateTime().toStringRfc3339()).isEqualTo("2026-09-28T15:00:00.000-05:00");
        assertThat(event.getEnd().getDateTime().toStringRfc3339()).isEqualTo("2026-09-28T16:00:00.000-05:00");
        assertThat(event.getAttendees()).singleElement()
                .satisfies(attendee -> assertThat(attendee.getEmail()).isEqualTo("ana@techcorp.com"));
    }

    @Test
    void dateWithoutOffsetIsInterpretedInLima() {
        Event event = service.buildEvent(new CalendarEventRequest("R", null,
                "2026-09-28T15:00:00", "2026-09-28T16:00:00", null));

        assertThat(event.getStart().getDateTime().toStringRfc3339()).isEqualTo("2026-09-28T15:00:00.000-05:00");
        assertThat(event.getAttendees()).isNull();
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.buildEvent(new CalendarEventRequest("R", null,
                "2026-09-28T16:00:00-05:00", "2026-09-28T15:00:00-05:00", null)))
                .isInstanceOf(InvalidCalendarEventException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void rejectsInvalidDate() {
        assertThatThrownBy(() -> service.buildEvent(new CalendarEventRequest("R", null,
                "lunes 3pm", "2026-09-28T16:00:00-05:00", null)))
                .isInstanceOf(InvalidCalendarEventException.class)
                .hasMessageContaining("startDateTime");
    }
}
