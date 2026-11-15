package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PriceWatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, Long> {

    Optional<PriceWatch> findByUserIdAndProductId(Long userId, Long productId);

    List<PriceWatch> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByProductId(Long productId);

    /** keyset page for the background check: id > afterId */
    @Query("select w from PriceWatch w where w.id > :afterId order by w.id")
    List<PriceWatch> page(Long afterId, Pageable pageable);
}
