package com.marketplace.backend.repository;

import com.marketplace.backend.entity.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    @Query("SELECT m FROM EmailMessage m WHERE m.status = com.marketplace.backend.entity.EmailMessage$Status.QUEUED "
            + "AND (m.nextAttemptAt IS NULL OR m.nextAttemptAt <= :now) ORDER BY m.id")
    List<EmailMessage> due(@Param("now") LocalDateTime now, Pageable pageable);

    // claims a message for sending (row lock until the sender's transaction ends): two senders - the scheduler and
    // the RabbitMQ consumer - never send the same e-mail twice
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE email_messages SET next_attempt_at = :until WHERE id = :id AND status = 'QUEUED' "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= :now)", nativeQuery = true)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Query("SELECT m FROM EmailMessage m WHERE (:status IS NULL OR m.status = :status) AND (:type = '' OR m.type = :type) "
            + "AND (:q = '' OR LOWER(m.toAddress) LIKE CONCAT('%', :q, '%') OR LOWER(m.subject) LIKE CONCAT('%', :q, '%'))")
    Page<EmailMessage> search(@Param("status") EmailMessage.Status status, @Param("type") String type, @Param("q") String q, Pageable pageable);

    // [status, count] since a time
    @Query("SELECT m.status, COUNT(m) FROM EmailMessage m WHERE m.createdAt > :since GROUP BY m.status")
    List<Object[]> countsSince(@Param("since") LocalDateTime since);
}
