package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShopVerification;
import com.marketplace.backend.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopVerificationRepository extends JpaRepository<ShopVerification, Long> {

    Optional<ShopVerification> findFirstByShopIdOrderByIdDesc(Long shopId);

    List<ShopVerification> findByStatusOrderBySubmittedAtAsc(VerificationStatus status);

    List<ShopVerification> findTop100ByStatusNotOrderByReviewedAtDesc(VerificationStatus status);
}
