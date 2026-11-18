package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    List<PushSubscription> findByUserId(Long userId);

    @Transactional
    long deleteByEndpoint(String endpoint);

    @Transactional
    long deleteByEndpointAndUserId(String endpoint, Long userId);
}
