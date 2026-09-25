package com.utp.assistant.automation.repository;

import java.util.List;

import com.utp.assistant.automation.entity.EmailAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailActionRepository extends JpaRepository<EmailAction, Long> {

    List<EmailAction> findByProcessedEmailIdOrderByIdAsc(Long processedEmailId);

    boolean existsByProcessedEmailIdAndToolName(Long processedEmailId, String toolName);
}
