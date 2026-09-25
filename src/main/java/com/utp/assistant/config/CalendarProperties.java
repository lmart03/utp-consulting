package com.utp.assistant.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.calendar")
public record CalendarProperties(String calendarId, ZoneId timeZone, String sendUpdates) {
}
