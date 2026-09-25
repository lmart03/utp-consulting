package com.utp.assistant.automation.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AutomationEvent", description = "Evento de observabilidad emitido durante el procesamiento de un correo.")
public record AutomationEvent(
        Long processedEmailId,
        String gmailMessageId,
        String subject,
        String from,
        String detectedCompany,
        AutomationStage stage,
        AutomationEventStatus status,
        String message,
        String toolName,
        String externalId,
        String externalUrl,
        String aiSummary,
        OffsetDateTime timestamp,
        Long elapsedMs,
        Map<String, Object> metadata,
        String error
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long processedEmailId;
        private String gmailMessageId;
        private String subject;
        private String from;
        private String detectedCompany;
        private AutomationStage stage;
        private AutomationEventStatus status;
        private String message;
        private String toolName;
        private String externalId;
        private String externalUrl;
        private String aiSummary;
        private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        private Long elapsedMs;
        private Map<String, Object> metadata;
        private String error;

        public Builder processedEmailId(Long processedEmailId) {
            this.processedEmailId = processedEmailId;
            return this;
        }

        public Builder gmailMessageId(String gmailMessageId) {
            this.gmailMessageId = gmailMessageId;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder detectedCompany(String detectedCompany) {
            this.detectedCompany = detectedCompany;
            return this;
        }

        public Builder stage(AutomationStage stage) {
            this.stage = stage;
            return this;
        }

        public Builder status(AutomationEventStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public Builder externalUrl(String externalUrl) {
            this.externalUrl = externalUrl;
            return this;
        }

        public Builder aiSummary(String aiSummary) {
            this.aiSummary = aiSummary;
            return this;
        }

        public Builder timestamp(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder elapsedMs(Long elapsedMs) {
            this.elapsedMs = elapsedMs;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public AutomationEvent build() {
            return new AutomationEvent(
                    processedEmailId,
                    gmailMessageId,
                    subject,
                    from,
                    detectedCompany,
                    stage,
                    status,
                    message,
                    toolName,
                    externalId,
                    externalUrl,
                    aiSummary,
                    timestamp != null ? timestamp : OffsetDateTime.now(ZoneOffset.UTC),
                    elapsedMs,
                    metadata,
                    error
            );
        }
    }
}
