package com.marketplace.backend.repository;

import com.marketplace.backend.entity.LuckySpin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LuckySpinRepository extends JpaRepository<LuckySpin, Long> {

    Optional<LuckySpin> findByUserIdAndSpinDate(Long userId, LocalDate spinDate);

    List<LuckySpin> findTop10ByUserIdOrderByIdDesc(Long userId);

    /** big wins of everyone, newest first ("Nguoi may man" ticker) */
    List<LuckySpin> findTop10ByCoinsGreaterThanEqualOrderByIdDesc(long coins);

    @Query("select coalesce(sum(s.coins), 0) from LuckySpin s where s.spinDate = :day")
    long coinsGivenOn(@Param("day") LocalDate day);

    long countBySpinDate(LocalDate day);

    /** Transaction-scoped lock: one prize decision at a time, so the daily budget cannot be overspent by a burst. */
    @Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(7502, 0)) l", nativeQuery = true)
    long lockBudget();
}
