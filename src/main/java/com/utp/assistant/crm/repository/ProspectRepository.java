package com.utp.assistant.crm.repository;

import java.util.List;
import java.util.Optional;

import com.utp.assistant.crm.entity.Prospect;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProspectRepository extends JpaRepository<Prospect, Long> {

    Optional<Prospect> findByEmail(String email);

    @Query("select p from Prospect p order by p.lastContactAt desc nulls last, p.id desc")
    List<Prospect> findLatest(Pageable pageable);

    @Query("""
            select p from Prospect p
            where lower(p.name) like :pattern or lower(p.email) like :pattern or lower(p.company) like :pattern
            order by p.lastContactAt desc nulls last, p.id desc""")
    List<Prospect> search(@Param("pattern") String lowercasePattern, Pageable pageable);
}
