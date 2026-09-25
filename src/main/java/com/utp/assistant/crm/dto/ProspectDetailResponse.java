package com.utp.assistant.crm.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Prospecto con su historial de interacciones (más reciente primero).")
public record ProspectDetailResponse(ProspectResponse prospect, List<ProspectInteractionResponse> interactions) {
}
