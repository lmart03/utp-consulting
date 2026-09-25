package com.utp.assistant.gmail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gmail")
public record GmailProperties(String applicationName, String unreadQuery, int maxResults) {
}
