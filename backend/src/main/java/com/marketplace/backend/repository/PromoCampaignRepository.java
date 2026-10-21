package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PromoCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, Long> {

    Optional<PromoCampaign> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<PromoCampaign> findAllByOrderByStartAtDesc();

    // shown to buyers: switched on and not over yet
    @Query("SELECT c FROM PromoCampaign c WHERE c.active = true AND c.endAt > :now ORDER BY c.startAt")
    List<PromoCampaign> visible(@Param("now") LocalDateTime now);
}
