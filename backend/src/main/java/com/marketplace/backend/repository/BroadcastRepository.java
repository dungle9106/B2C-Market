package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Broadcast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
    List<Broadcast> findTop50ByOrderByCreatedAtDesc();
}
