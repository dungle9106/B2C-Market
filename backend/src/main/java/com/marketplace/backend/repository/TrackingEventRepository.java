package com.marketplace.backend.repository;

import com.marketplace.backend.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

    List<TrackingEvent> findByOrderIdIn(Collection<Long> orderIds);
}
