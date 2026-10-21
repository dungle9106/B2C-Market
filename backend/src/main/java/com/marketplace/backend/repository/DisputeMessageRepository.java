package com.marketplace.backend.repository;

import com.marketplace.backend.entity.DisputeMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeMessageRepository extends JpaRepository<DisputeMessage, Long> {

    List<DisputeMessage> findByReturnRequestIdOrderByIdAsc(Long returnRequestId);

    long countByReturnRequestId(Long returnRequestId);

    boolean existsByReturnRequestIdAndSide(Long returnRequestId, DisputeMessage.Side side);
}
