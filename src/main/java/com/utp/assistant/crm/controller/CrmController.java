package com.utp.assistant.crm.controller;

import java.util.List;

import com.utp.assistant.crm.dto.ProspectDetailResponse;
import com.utp.assistant.crm.dto.ProspectRequest;
import com.utp.assistant.crm.dto.ProspectResponse;
import com.utp.assistant.crm.service.CrmService;
import com.utp.assistant.crm.service.CrmService.UpsertResult;
import com.utp.assistant.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_CRM)
public class CrmController {

    private static final String EXAMPLE_PROSPECT = """
            {
              "email": "carla.mendoza@andinalogistics.com",
              "name": "Carla Mendoza",
              "company": "Andina Logistics",
              "phone": "+51 987 654 321",
              "status": "INTERESTED",
              "notes": "Solicita estimación para integrar facturación electrónica."
            }""";

    private final CrmService crmService;

    @Operation(summary = "Listar prospectos",
            description = "Contactos del CRM ordenados por último contacto. Permite buscar por nombre, email o empresa.")
    @ApiResponse(responseCode = "200", description = "Prospectos.")
    @GetMapping("/prospects")
    public List<ProspectResponse> list(
            @Parameter(description = "Texto a buscar (opcional).", example = "andina") @RequestParam(required = false) String search,
            @Parameter(description = "Cantidad máxima.", example = "50") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return crmService.list(search, limit);
    }

    @Operation(summary = "Detalle de un prospecto", description = "Ficha del contacto con su historial de interacciones.")
    @ApiResponse(responseCode = "200", description = "Prospecto e interacciones.")
    @ApiResponse(responseCode = "404", description = "No existe el prospecto.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/prospects/{id}")
    public ProspectDetailResponse detail(@Parameter(example = "1") @PathVariable Long id) {
        return crmService.detail(id);
    }

    @Operation(summary = "Crear o actualizar prospecto (manual)",
            description = "Upsert por email para probar el CRM desde Swagger. Responde 201 si lo crea y 200 si lo actualiza.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProspectRequest.class),
                    examples = @ExampleObject(name = "Nuevo prospecto", value = EXAMPLE_PROSPECT))))
    @ApiResponse(responseCode = "201", description = "Prospecto creado.")
    @ApiResponse(responseCode = "200", description = "Prospecto actualizado.")
    @ApiResponse(responseCode = "400", description = "Datos inválidos.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/prospects")
    public ResponseEntity<ProspectResponse> upsert(@Valid @RequestBody ProspectRequest request) {
        UpsertResult result = crmService.upsertManual(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.prospect());
    }
}
