package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AdTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdTransactionRepository extends JpaRepository<AdTransaction, Long> {
    Page<AdTransaction> findByShopIdOrderByIdDesc(Long shopId, Pageable pageable);
}
