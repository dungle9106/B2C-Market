package com.marketplace.backend.service;

import com.marketplace.backend.entity.ProductEvent;
import com.marketplace.backend.repository.ProductEventRepository;
import com.marketplace.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Records product views / add-to-cart for the seller's traffic report. Best-effort: never breaks the page.
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEventService {

    static final int DEDUPE_MINUTES = 30;

    private final ProductEventRepository eventRepository;
    private final ShopRepository shopRepository;

    @Transactional
    public void record(ProductEvent.Type type, Long productId, Long shopId, Long userId, String visitorKey) {
        if (productId == null || shopId == null || visitorKey == null) return;
        try {
            // the shop looking at its own products is not traffic
            if (userId != null && shopRepository.existsByIdAndSellerId(shopId, userId)) return;
            if (eventRepository.existsByProductIdAndVisitorKeyAndTypeAndCreatedAtAfter(productId, visitorKey, type,
                    LocalDateTime.now().minusMinutes(DEDUPE_MINUTES))) return;
            eventRepository.save(ProductEvent.builder().productId(productId).shopId(shopId).type(type)
                    .userId(userId).visitorKey(visitorKey).build());
        } catch (RuntimeException e) {
            log.warn("Could not record {} for product {}: {}", type, productId, e.getMessage());
        }
    }
}
