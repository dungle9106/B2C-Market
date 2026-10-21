package com.marketplace.backend.repository;

import com.marketplace.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserId(Long userId);
    Optional<CartItem> findByUserIdAndProductIdAndVariantId(Long userId, Long productId, Long variantId);
    void deleteByUserIdAndIdIn(Long userId, List<Long> ids);

    // when a seller removes a variant, carts holding it can't check out anymore
    void deleteByVariantIdIn(Collection<Long> variantIds);

    void deleteByProductId(Long productId);
}
