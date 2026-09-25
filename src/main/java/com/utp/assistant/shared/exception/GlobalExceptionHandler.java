package com.utp.assistant.shared.exception;

import com.utp.assistant.assistant.exception.GeminiApiException;
import com.utp.assistant.assistant.exception.GeminiNotConfiguredException;
import com.utp.assistant.auth.exception.GmailAuthorizationException;
import com.utp.assistant.auth.exception.GoogleScopeMissingException;
import com.utp.assistant.calendar.exception.CalendarApiException;
import com.utp.assistant.calendar.exception.InvalidCalendarEventException;
import com.utp.assistant.crm.exception.InvalidProspectException;
import com.utp.assistant.crm.exception.ProspectNotFoundException;
import com.utp.assistant.gmail.exception.GmailApiException;
import com.utp.assistant.gmail.exception.GmailMessageNotFoundException;
import com.utp.assistant.gmail.exception.MimeDecodingException;
import com.utp.assistant.jira.exception.JiraApiException;
import com.utp.assistant.jira.exception.JiraNotConfiguredException;
import com.utp.assistant.reply.exception.InvalidReplyStateException;
import com.utp.assistant.reply.exception.ReplyNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 9457 (ProblemDetail) sin exponer stack traces.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GmailAuthorizationException.class)
    ProblemDetail handleAuthorization(GmailAuthorizationException ex) {
        log.warn("Autorización de Google no disponible: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "No autorizado", ex.getMessage());
    }

    @ExceptionHandler(GmailMessageNotFoundException.class)
    ProblemDetail handleNotFound(GmailMessageNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Mensaje no encontrado", ex.getMessage());
    }

    @ExceptionHandler(GmailApiException.class)
    ProblemDetail handleGmailApi(GmailApiException ex) {
        log.error("Error de Gmail API: {}", ex.getMessage(), ex);
        return problem(HttpStatus.BAD_GATEWAY, "Error de Gmail API", ex.getMessage());
    }

    @ExceptionHandler(MimeDecodingException.class)
    ProblemDetail handleMime(MimeDecodingException ex) {
        log.error("Error decodificando MIME: {}", ex.getMessage(), ex);
        return problem(HttpStatus.BAD_GATEWAY, "Contenido de correo no procesable", ex.getMessage());
    }

    @ExceptionHandler(InvalidCalendarEventException.class)
    ProblemDetail handleInvalidCalendarEvent(InvalidCalendarEventException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Evento inválido", ex.getMessage());
    }

    @ExceptionHandler(GoogleScopeMissingException.class)
    ProblemDetail handleScopeMissing(GoogleScopeMissingException ex) {
        log.warn("Permiso de Google insuficiente: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Permiso de Google insuficiente", ex.getMessage());
    }

    @ExceptionHandler(CalendarApiException.class)
    ProblemDetail handleCalendarApi(CalendarApiException ex) {
        log.error("Error de Google Calendar API: {}", ex.getMessage(), ex);
        return problem(HttpStatus.BAD_GATEWAY, "Error de Google Calendar API", ex.getMessage());
    }

    @ExceptionHandler(JiraApiException.class)
    ProblemDetail handleJiraApi(JiraApiException ex) {
        log.warn("Error de Jira: {}", ex.getMessage());
        return problem(ex.getStatus(), "Error de Jira", ex.getMessage());
    }

    @ExceptionHandler(InvalidProspectException.class)
    ProblemDetail handleInvalidProspect(InvalidProspectException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Prospecto inválido", ex.getMessage());
    }

    @ExceptionHandler(ProspectNotFoundException.class)
    ProblemDetail handleProspectNotFound(ProspectNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Prospecto no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ReplyNotFoundException.class)
    ProblemDetail handleReplyNotFound(ReplyNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Respuesta no encontrada", ex.getMessage());
    }

    @ExceptionHandler(InvalidReplyStateException.class)
    ProblemDetail handleInvalidReplyState(InvalidReplyStateException ex) {
        return problem(HttpStatus.CONFLICT, "Estado de la respuesta", ex.getMessage());
    }

    @ExceptionHandler(JiraNotConfiguredException.class)
    ProblemDetail handleJiraNotConfigured(JiraNotConfiguredException ex) {
        log.warn(ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Jira no configurado", ex.getMessage());
    }

    @ExceptionHandler(GeminiNotConfiguredException.class)
    ProblemDetail handleGeminiNotConfigured(GeminiNotConfiguredException ex) {
        log.warn(ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Gemini no configurado", ex.getMessage());
    }

    @ExceptionHandler(GeminiApiException.class)
    ProblemDetail handleGeminiApi(GeminiApiException ex) {
        if (ex.getStatus().is5xxServerError() && ex.getStatus() != HttpStatus.BAD_GATEWAY
                || ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            // Saturación/cuota/timeout de Google: transitorio, sin stack trace.
            log.warn("Gemini no disponible: {}", ex.getMessage());
        } else {
            log.error("Error de Gemini API: {}", ex.getMessage(), ex);
        }
        return problem(ex.getStatus(), "Error de Gemini API", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
