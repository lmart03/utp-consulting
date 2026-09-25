package com.utp.assistant.crm.repository;

import java.util.List;
import java.util.Optional;

import com.utp.assistant.crm.entity.ProspectInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProspectInteractionRepository extends JpaRepository<ProspectInteraction, Long> {

    List<ProspectInteraction> findByProspectIdOrderByCreatedAtDesc(Long prospectId);

    Optional<ProspectInteraction> findFirstByGmailMessageId(String gmailMessageId);
}
