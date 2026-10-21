package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ErrorEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long> {

    Optional<ErrorEvent> findByFingerprint(String fingerprint);

    @Query("SELECT e FROM ErrorEvent e WHERE (:status IS NULL OR e.status = :status) "
            + "AND (:q = '' OR LOWER(e.exceptionType) LIKE CONCAT('%', :q, '%') OR LOWER(e.message) LIKE CONCAT('%', :q, '%') "
            + "OR LOWER(e.lastPath) LIKE CONCAT('%', :q, '%'))")
    Page<ErrorEvent> search(@Param("status") ErrorEvent.Status status, @Param("q") String q, Pageable pageable);

    long countByStatus(ErrorEvent.Status status);
}
