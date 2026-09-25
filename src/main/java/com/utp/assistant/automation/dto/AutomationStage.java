package com.utp.assistant.automation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AutomationStage", description = "Etapas del pipeline de procesamiento de correos.")
public enum AutomationStage {
    EMAIL_DETECTED,
    EMAIL_CLAIMED,

    AI_ANALYSIS_STARTED,
    AI_ANALYSIS_COMPLETED,

    TOOL_STARTED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    TOOL_SKIPPED,

    REPLY_DRAFT_STARTED,
    REPLY_DRAFT_COMPLETED,
    REPLY_DRAFT_FAILED,
    REPLY_DRAFT_SKIPPED,

    MARK_READ_STARTED,
    MARK_READ_COMPLETED,
    MARK_READ_FAILED,

    PROCESS_COMPLETED,
    PROCESS_PARTIAL,
    PROCESS_FAILED,
    PROCESS_IGNORED
}
