package com.marketplace.backend.repository;

import com.marketplace.backend.entity.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

    List<LoginEvent> findTop30ByUserIdOrderByIdDesc(Long userId);

    // failed attempts from one address since a time (per-IP limit)
    @Query("SELECT COUNT(e) FROM LoginEvent e WHERE e.ip = :ip AND e.createdAt > :since AND e.result IN :results")
    long countByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since, @Param("results") Collection<LoginEvent.Result> results);

    @Query("SELECT e FROM LoginEvent e WHERE e.createdAt > :since AND (:failedOnly = false OR e.result <> com.marketplace.backend.entity.LoginEvent$Result.SUCCESS)")
    Page<LoginEvent> recent(@Param("since") LocalDateTime since, @Param("failedOnly") boolean failedOnly, Pageable pageable);

    // [ip, failed attempts, distinct emails] since a time
    @Query("SELECT e.ip, COUNT(e), COUNT(DISTINCT e.email) FROM LoginEvent e WHERE e.createdAt > :since "
            + "AND e.result <> com.marketplace.backend.entity.LoginEvent$Result.SUCCESS GROUP BY e.ip ORDER BY COUNT(e) DESC")
    List<Object[]> failuresByIp(@Param("since") LocalDateTime since, Pageable pageable);

    // [result, count] since a time
    @Query("SELECT e.result, COUNT(e) FROM LoginEvent e WHERE e.createdAt > :since GROUP BY e.result")
    List<Object[]> countsSince(@Param("since") LocalDateTime since);
}
