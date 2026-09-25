package com.utp.assistant.repository;

import java.util.List;

import com.utp.assistant.entity.EmailAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailActionRepository extends JpaRepository<EmailAction, Long> {

    List<EmailAction> findByProcessedEmailIdOrderByIdAsc(Long processedEmailId);

    boolean existsByProcessedEmailIdAndToolName(Long processedEmailId, String toolName);
}
