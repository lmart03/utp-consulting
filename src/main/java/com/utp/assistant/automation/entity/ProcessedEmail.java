package com.utp.assistant.automation.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de un correo de Gmail tomado por la automatización. La UNIQUE sobre gmail_message_id es la
 * protección final contra procesar dos veces el mismo correo. No se guarda el cuerpo del correo.
 */
@Entity
@Table(name = "processed_emails",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_emails_gmail_message_id", columnNames = "gmail_message_id"),
        indexes = @Index(name = "ix_processed_emails_status_next_retry", columnList = "status, next_retry_at"))
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gmail_message_id", nullable = false, length = 128)
    private String gmailMessageId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "from_address", length = 512)
    private String fromAddress;

    @Column(length = 1000)
    private String subject;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessedEmailStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Próximo reintento; mientras está PROCESSING actúa como lease (si vence, el correo se retoma). */
    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    /** Momento en que Gemini analizó el correo con éxito; si es null, el reintento vuelve a llamar a Gemini. */
    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "gmail_marked_read", nullable = false)
    private boolean gmailMarkedRead;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

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
