package com.marketplace.backend.repository;

import com.marketplace.backend.entity.CoinTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {

    Page<CoinTransaction> findByUserId(Long userId, Pageable pageable);
}
