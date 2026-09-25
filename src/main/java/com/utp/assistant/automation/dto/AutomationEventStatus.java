package com.utp.assistant.automation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AutomationEventStatus", description = "Estados de los eventos de observabilidad.")
public enum AutomationEventStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    SKIPPED
}
