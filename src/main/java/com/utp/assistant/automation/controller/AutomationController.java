package com.utp.assistant.automation.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.utp.assistant.assistant.config.GeminiProperties;
import com.utp.assistant.auth.service.GoogleAutomationAccount;
import com.utp.assistant.automation.config.AssistantProperties;
import com.utp.assistant.automation.dto.AutomationDtos.EmailActionDto;
import com.utp.assistant.automation.dto.AutomationDtos.IntegrationInfo;
import com.utp.assistant.automation.dto.AutomationDtos.IntegrationsStatusDto;
import com.utp.assistant.automation.dto.AutomationDtos.ProcessedEmailDetailDto;
import com.utp.assistant.automation.dto.AutomationDtos.ProcessedEmailDto;
import com.utp.assistant.automation.dto.AutomationDtos.RunResultDto;
import com.utp.assistant.automation.dto.AutomationDtos.StatusDto;
import com.utp.assistant.automation.entity.EmailAction;
import com.utp.assistant.automation.entity.ProcessedEmail;
import com.utp.assistant.automation.entity.ProcessedEmailStatus;
import com.utp.assistant.automation.service.EmailAutomationService;
import com.utp.assistant.automation.service.EmailAutomationService.CycleResult;
import com.utp.assistant.automation.service.ProcessedEmailStore;
import com.utp.assistant.calendar.config.CalendarProperties;
import com.utp.assistant.jira.config.JiraProperties;
import com.utp.assistant.reply.dto.EmailReplyDto;
import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.service.ReplyService;
import com.utp.assistant.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTOMATION)
public class AutomationController {

    private final EmailAutomationService automationService;
    private final ProcessedEmailStore store;
    private final GoogleAutomationAccount automationAccount;
    private final AssistantProperties properties;
    private final JiraProperties jiraProperties;
    private final GeminiProperties geminiProperties;
    private final CalendarProperties calendarProperties;
    private final ReplyService replyService;

    @Operation(summary = "Estado de la automatización",
            description = "Muestra si el polling está activo, el cutoff de correos, la cuenta Google usada y cuántos "
                    + "correos hay en cada estado.")
    @ApiResponse(responseCode = "200", description = "Estado actual.")
    @GetMapping("/status")
    public StatusDto status() {
        Map<ProcessedEmailStatus, Long> counts = store.countByStatus();
        long processed = counts.getOrDefault(ProcessedEmailStatus.PROCESSED, 0L);
        long ignored = counts.getOrDefault(ProcessedEmailStatus.IGNORED, 0L);
        long partial = counts.getOrDefault(ProcessedEmailStatus.PARTIAL, 0L);
        long failed = counts.getOrDefault(ProcessedEmailStatus.FAILED, 0L);
        long processing = counts.getOrDefault(ProcessedEmailStatus.PROCESSING, 0L);
        long totalToday = processed + ignored + partial + failed;

        return new StatusDto(
                properties.polling().enabled(),
                properties.polling().delayMs(),
                automationService.automationStartedAt(),
                properties.automation().processOldEmails(),
                automationAccount.current().map(GoogleAutomationAccount.Account::email).orElse(null),
                automationService.isRunning(),
                automationService.lastResult().map(AutomationController::toDto).orElse(null),
                processing,
                processed,
                ignored,
                partial,
                failed,
                totalToday);
    }

    @Operation(summary = "Estado de las integraciones",
            description = "Estado de configuración y conectividad de Gmail, Gemini, Jira y Google Calendar.")
    @ApiResponse(responseCode = "200", description = "Estado de integraciones.")
    @GetMapping("/integrations")
    public IntegrationsStatusDto integrations() {
        Optional<GoogleAutomationAccount.Account> account = automationAccount.current();
        boolean googleConnected = account.isPresent();
        String googleEmail = account.map(GoogleAutomationAccount.Account::email).orElse(null);

        return new IntegrationsStatusDto(
                IntegrationInfo.ofGmail(googleConnected, googleEmail),
                IntegrationInfo.ofGemini(geminiProperties.isConfigured(), geminiProperties.model()),
                IntegrationInfo.ofJira(jiraProperties.isConfigured(), jiraProperties.projectKey(), jiraProperties.normalizedBaseUrl()),
                IntegrationInfo.ofCalendar(googleConnected, calendarProperties.calendarId(), calendarProperties.timeZone().getId()),
                IntegrationInfo.ofCrm()
        );
    }

    @Operation(summary = "Correos procesados",
            description = "Últimos correos registrados por la automatización, del más reciente al más antiguo.")
    @ApiResponse(responseCode = "200", description = "Lista de correos procesados.")
    @GetMapping("/emails")
    public List<ProcessedEmailDto> emails(@Parameter(description = "Cantidad máxima.", example = "50")
                                         @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        List<ProcessedEmail> emails = store.latest(limit);
        Map<Long, EmailReply> replies = replyService.findByProcessedEmailIds(emails.stream().map(ProcessedEmail::getId).toList());
        return emails.stream()
                .map(email -> ProcessedEmailDto.from(email, store.actions(email.getId()), replies.get(email.getId())))
                .toList();
    }

    @Operation(summary = "Detalle de un correo procesado",
            description = "Correo registrado con cada herramienta solicitada por Gemini (Jira, Calendar, CRM) y su resultado.")
    @ApiResponse(responseCode = "200", description = "Correo y acciones.")
    @ApiResponse(responseCode = "404", description = "No existe el registro.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/emails/{id}")
    public ProcessedEmailDetailDto email(@Parameter(description = "Id interno (no el id de Gmail).", example = "1")
                                         @PathVariable Long id) {
        return store.findById(id)
                .map(email -> {
                    List<EmailAction> actions = store.actions(id);
                    EmailReply reply = replyService.findByProcessedEmailId(id).orElse(null);
                    return new ProcessedEmailDetailDto(
                            ProcessedEmailDto.from(email, actions, reply),
                            actions.stream().map(a -> EmailActionDto.from(a, jiraProperties.normalizedBaseUrl())).toList(),
                            reply != null ? EmailReplyDto.from(reply) : null);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el correo procesado " + id + "."));
    }

    @Operation(summary = "Procesar ahora",
            description = "Ejecuta inmediatamente un ciclo del polling (útil para la demo). Si ya hay un ciclo en curso "
                    + "(scheduler u otra llamada) no se ejecuta otro en paralelo y responde 409.")
    @ApiResponse(responseCode = "200", description = "Ciclo ejecutado (o no ejecutado por falta de login/configuración; ver message).")
    @ApiResponse(responseCode = "409", description = "Ya hay un ciclo en ejecución.")
    @PostMapping("/process-now")
    public ResponseEntity<RunResultDto> processNow() {
        if (automationService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(toDto(new CycleResult(false, "Ya hay un ciclo de automatización en ejecución.", 0, 0, 0, 0, null)));
        }
        CycleResult result = automationService.runCycle();
        boolean concurrent = !result.executed() && result.message().startsWith("Ya hay un ciclo");
        return ResponseEntity.status(concurrent ? HttpStatus.CONFLICT : HttpStatus.OK).body(toDto(result));
    }

    private static RunResultDto toDto(CycleResult result) {
        return new RunResultDto(result.executed(), result.message(), result.newEmails(), result.retried(),
                result.skipped(), result.errors(), result.finishedAt());
    }
}

