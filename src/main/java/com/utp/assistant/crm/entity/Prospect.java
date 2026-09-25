package com.utp.assistant.crm.entity;

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

/** Contacto comercial del CRM. Un registro por email (UNIQUE), actualizado con cada correo recibido. */
@Entity
@Table(name = "prospects", uniqueConstraints = @UniqueConstraint(name = "uk_prospects_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Prospect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email real del remitente (header From), en minúsculas. */
    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", length = 20)
    private DataSource nameSource;

    @Column(length = 255)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_source", length = 20)
    private DataSource companySource;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProspectStatus status;

    /** Notas del último contacto (el historial completo está en prospect_interactions). */
    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "last_subject", length = 1000)
    private String lastSubject;

    @Column(name = "last_gmail_message_id", length = 128)
    private String lastGmailMessageId;

    @Column(name = "last_contact_at")
    private OffsetDateTime lastContactAt;

    @Column(name = "interaction_count", nullable = false)
    private int interactionCount;

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
