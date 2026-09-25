package com.utp.assistant.reply.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta sugerida para un correo procesado. UNIQUE(processed_email_id): un solo borrador por correo, así un
 * reintento de la automatización nunca genera (ni envía) dos respuestas.
 */
@Entity
@Table(name = "email_replies",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_replies_processed_email", columnNames = "processed_email_id"))
@Getter
@Setter
@NoArgsConstructor
public class EmailReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "processed_email_id", nullable = false)
    private Long processedEmailId;

    @Column(name = "gmail_message_id", nullable = false, length = 128)
    private String gmailMessageId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    /** Dirección del remitente original (solo la dirección, sin nombre visible). */
    @Column(name = "to_address", nullable = false, length = 320)
    private String toAddress;

    @Column(length = 1000)
    private String subject;

    /** Texto actual (el generado por Gemini o el editado por el usuario antes de enviar). */
    @Column(columnDefinition = "text")
    private String body;

    /** Texto original de Gemini, para saber si el usuario lo editó. */
    @Column(name = "ai_body", columnDefinition = "text")
    private String aiBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplyStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** Resumen IA y acciones realizadas: contexto guardado para poder regenerar el borrador. */
    @Column(name = "context_summary", columnDefinition = "text")
    private String contextSummary;

    @Column(name = "context_facts", columnDefinition = "text")
    private String contextFacts;

    @Column(name = "sent_gmail_message_id", length = 128)
    private String sentGmailMessageId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
