package com.utp.assistant.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.utp.assistant.entity.ProcessedEmail;
import com.utp.assistant.entity.ProcessedEmailStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, Long> {

    Optional<ProcessedEmail> findByGmailMessageId(String gmailMessageId);

    boolean existsByGmailMessageId(String gmailMessageId);

    long countByStatus(ProcessedEmailStatus status);

    List<ProcessedEmail> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Correos cuyo reintento (o lease de PROCESSING) ya venció. */
    @Query("""
            select e from ProcessedEmail e
            where e.nextRetryAt is not null and e.nextRetryAt <= :now and e.status in :statuses
            order by e.nextRetryAt asc""")
    List<ProcessedEmail> findDue(@Param("now") OffsetDateTime now,
                                 @Param("statuses") Collection<ProcessedEmailStatus> statuses,
                                 Pageable pageable);

    /**
     * Re-claim atómico de un correo vencido: solo un worker logra pasar de expectedStatus a PROCESSING.
     * Devuelve 1 si este worker obtuvo el correo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProcessedEmail e
            set e.status = com.utp.assistant.entity.ProcessedEmailStatus.PROCESSING,
                e.nextRetryAt = :leaseUntil, e.attemptCount = e.attemptCount + 1, e.updatedAt = :now
            where e.id = :id and e.status = :expectedStatus and e.nextRetryAt is not null and e.nextRetryAt <= :now""")
    int reclaim(@Param("id") Long id,
                @Param("expectedStatus") ProcessedEmailStatus expectedStatus,
                @Param("now") OffsetDateTime now,
                @Param("leaseUntil") OffsetDateTime leaseUntil);
}
