package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("SELECT COALESCE(SUM(l.costUsd), 0) FROM AiUsageLog l WHERE l.createdAt >= :from AND l.createdAt < :to")
    BigDecimal costBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<AiUsageLog> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    @Query("SELECT l FROM AiUsageLog l WHERE (:status IS NULL OR l.status = :status) AND (:feature = '' OR l.feature = :feature)")
    Page<AiUsageLog> search(@Param("status") AiUsageLog.Status status, @Param("feature") String feature, Pageable pageable);
}
