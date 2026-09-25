package com.utp.assistant.crm.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.utp.assistant.crm.entity.DataSource;
import com.utp.assistant.crm.entity.Prospect;
import com.utp.assistant.crm.entity.ProspectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contacto del CRM.")
public record ProspectResponse(
        Long id,
        @Schema(example = "carla.mendoza@andinalogistics.com") String email,
        @Schema(example = "Carla Mendoza") String name,
        @Schema(description = "Origen del nombre: GEMINI, FROM_HEADER, EMAIL_ADDRESS o MANUAL.") DataSource nameSource,
        @Schema(example = "Andina Logistics") String company,
        @Schema(description = "Origen de la empresa: GEMINI, EMAIL_DOMAIN o MANUAL.") DataSource companySource,
        String phone,
        @Schema(example = "MEETING_SCHEDULED") ProspectStatus status,
        String notes,
        String lastSubject,
        String lastGmailMessageId,
        OffsetDateTime lastContactAt,
        int interactionCount,
        @Schema(description = "Datos que el correo no incluyó (para completar manualmente).", example = "[\"phone\"]")
        List<String> missingFields,
        @Schema(description = "Datos deducidos del header From o del dominio, no escritos por el contacto.", example = "[\"company\"]")
        List<String> inferredFields,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProspectResponse from(Prospect p) {
        List<String> missing = new ArrayList<>();
        if (isBlank(p.getName())) {
            missing.add("name");
        }
        if (isBlank(p.getCompany())) {
            missing.add("company");
        }
        if (isBlank(p.getPhone())) {
            missing.add("phone");
        }
        List<String> inferred = new ArrayList<>();
        if (p.getNameSource() != null && p.getNameSource().isInferred()) {
            inferred.add("name");
        }
        if (p.getCompanySource() != null && p.getCompanySource().isInferred()) {
            inferred.add("company");
        }
        return new ProspectResponse(p.getId(), p.getEmail(), p.getName(), p.getNameSource(), p.getCompany(),
                p.getCompanySource(), p.getPhone(), p.getStatus(), p.getNotes(), p.getLastSubject(),
                p.getLastGmailMessageId(), p.getLastContactAt(), p.getInteractionCount(), List.copyOf(missing),
                List.copyOf(inferred), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
