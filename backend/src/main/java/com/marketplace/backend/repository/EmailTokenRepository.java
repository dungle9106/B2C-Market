package com.marketplace.backend.repository;

import com.marketplace.backend.entity.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    // a new link makes the older ones of the same purpose useless
    @Modifying
    @Query("UPDATE EmailToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int expireAll(@Param("userId") Long userId, @Param("purpose") EmailToken.Purpose purpose, @Param("now") LocalDateTime now);
}
