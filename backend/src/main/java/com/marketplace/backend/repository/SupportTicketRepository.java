package com.marketplace.backend.repository;

import com.marketplace.backend.entity.SupportTicket;
import com.marketplace.backend.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

    Page<SupportTicket> findByRequesterIdAndStatusInOrderByUpdatedAtDesc(Long requesterId, Collection<TicketStatus> statuses, Pageable pageable);

    boolean existsByCode(String code);

    // RESOLVED tickets nobody touched for a while get closed automatically
    List<SupportTicket> findByStatusAndResolvedAtBefore(TicketStatus status, LocalDateTime before);

    @Query("SELECT t.status, COUNT(t) FROM SupportTicket t GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query("SELECT AVG(t.rating), COUNT(t.rating) FROM SupportTicket t WHERE t.rating IS NOT NULL")
    List<Object[]> ratingSummary();
}
