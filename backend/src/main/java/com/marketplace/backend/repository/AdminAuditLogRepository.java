package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId, Pageable pageable);

    // all entries, or one admin's; newest first
    @Query("SELECT a FROM AdminAuditLog a WHERE (:adminId IS NULL OR a.adminId = :adminId) "
            + "AND (:targetType IS NULL OR a.targetType = :targetType) ORDER BY a.id DESC")
    Page<AdminAuditLog> search(@Param("adminId") Long adminId, @Param("targetType") String targetType, Pageable pageable);
}
