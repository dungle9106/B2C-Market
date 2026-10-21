package com.marketplace.backend.repository;

import com.marketplace.backend.entity.InventoryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {

    Page<InventoryLog> findByShopIdOrderByIdDesc(Long shopId, Pageable pageable);

    Page<InventoryLog> findByShopIdAndProductIdOrderByIdDesc(Long shopId, Long productId, Pageable pageable);
}
