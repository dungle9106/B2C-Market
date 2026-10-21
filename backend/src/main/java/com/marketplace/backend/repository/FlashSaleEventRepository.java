package com.marketplace.backend.repository;

import com.marketplace.backend.entity.FlashSaleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashSaleEventRepository extends JpaRepository<FlashSaleEvent, Long> {

    List<FlashSaleEvent> findAllByOrderByStartAtDesc();

    // not cancelled and not over: what sellers and buyers see
    @Query("SELECT e FROM FlashSaleEvent e WHERE e.cancelled = false AND e.endAt > :now ORDER BY e.startAt")
    List<FlashSaleEvent> upcomingOrRunning(@Param("now") LocalDateTime now);

    // another slot whose time overlaps [start, end)
    @Query("SELECT e FROM FlashSaleEvent e WHERE e.cancelled = false AND e.id <> :id AND e.startAt < :end AND e.endAt > :start")
    List<FlashSaleEvent> overlapping(@Param("id") Long id, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<FlashSaleEvent> findByCampaignIdAndCancelledFalseOrderByStartAt(Long campaignId);

    long countByCampaignId(Long campaignId);
}
