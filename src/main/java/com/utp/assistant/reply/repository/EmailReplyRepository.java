package com.utp.assistant.reply.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.utp.assistant.reply.entity.EmailReply;
import com.utp.assistant.reply.entity.ReplyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailReplyRepository extends JpaRepository<EmailReply, Long> {

    Optional<EmailReply> findByProcessedEmailId(Long processedEmailId);

    List<EmailReply> findByProcessedEmailIdIn(Collection<Long> processedEmailIds);

    /** DRAFT → SENDING de forma atómica: si dos clics llegan a la vez, solo uno gana y envía. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailReply r set r.status = :sending, r.updatedAt = :now
            where r.id = :id and r.status = :draft""")
    int claimForSending(@Param("id") Long id, @Param("now") OffsetDateTime now,
                        @Param("draft") ReplyStatus draft, @Param("sending") ReplyStatus sending);
}
