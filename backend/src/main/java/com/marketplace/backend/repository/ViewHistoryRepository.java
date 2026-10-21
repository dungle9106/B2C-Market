package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {
    List<ViewHistory> findTop10ByUserIdOrderByViewedAtDesc(Long userId);

    void deleteByProductId(Long productId);
}
