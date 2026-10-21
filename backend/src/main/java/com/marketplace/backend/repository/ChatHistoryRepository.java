package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChatHistory> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
