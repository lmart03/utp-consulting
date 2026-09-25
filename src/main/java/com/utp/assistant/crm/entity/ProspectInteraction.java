package com.utp.assistant.crm.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historial del prospecto: una fila por correo. UNIQUE(prospect_id, gmail_message_id) hace idempotente la acción
 * CRM de la automatización (un reintento del mismo correo no duplica notas ni contadores).
 */
@Entity
@Table(name = "prospect_interactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_prospect_interactions_email", columnNames = {"prospect_id", "gmail_message_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProspectInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prospect_id", nullable = false)
    private Prospect prospect;

    /** Null en altas manuales. */
    @Column(name = "gmail_message_id", length = 128)
    private String gmailMessageId;

    @Column(length = 1000)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", length = 20)
    private ProspectStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 20)
    private ProspectStatus statusAfter;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
