package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShoppiPayTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppiPayTransactionRepository extends JpaRepository<ShoppiPayTransaction, Long> {

    Page<ShoppiPayTransaction> findByUserId(Long userId, Pageable pageable);
}
