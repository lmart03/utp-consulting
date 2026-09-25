package com.utp.assistant.crm.dto;

import com.utp.assistant.crm.entity.ProspectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta o actualización manual de un prospecto (upsert por email).")
public record ProspectRequest(
        @Schema(example = "carla.mendoza@andinalogistics.com") @NotBlank @Email @Size(max = 320) String email,
        @Schema(example = "Carla Mendoza") @Size(max = 255) String name,
        @Schema(example = "Andina Logistics") @Size(max = 255) String company,
        @Schema(example = "+51 987 654 321") @Size(max = 50) String phone,
        @Schema(description = "Si se omite: NEW para contactos nuevos, sin cambio para existentes.", example = "INTERESTED")
        ProspectStatus status,
        @Schema(example = "Solicita estimación para integrar facturación electrónica.") @Size(max = 4000) String notes) {
}
