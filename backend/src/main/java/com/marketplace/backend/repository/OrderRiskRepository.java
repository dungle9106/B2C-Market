package com.marketplace.backend.repository;

import com.marketplace.backend.entity.OrderRisk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRiskRepository extends JpaRepository<OrderRisk, Long> {
    Optional<OrderRisk> findByOrderId(Long orderId);
}
