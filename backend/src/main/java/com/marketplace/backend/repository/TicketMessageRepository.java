package com.marketplace.backend.repository;

import com.marketplace.backend.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);
}
