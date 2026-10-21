package com.marketplace.backend.repository;

import com.marketplace.backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now ORDER BY s.lastUsedAt DESC")
    List<UserSession> active(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    boolean existsByUserIdAndDeviceKey(Long userId, String deviceKey);
}
