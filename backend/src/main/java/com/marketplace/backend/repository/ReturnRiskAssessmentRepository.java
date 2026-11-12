package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReturnRiskAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReturnRiskAssessmentRepository extends JpaRepository<ReturnRiskAssessment, Long> {

    Optional<ReturnRiskAssessment> findByReturnRequestId(Long returnRequestId);

    long countByBuyerIdAndLabelAndReturnRequestIdNot(Long buyerId, String label, Long returnRequestId);

    long countByLevel(String level);

    long countByLabel(String label);

    long countByLevelAndLabelIsNull(String level);

    // label: null = any, "" = not reviewed yet
    @Query("SELECT a FROM ReturnRiskAssessment a WHERE (:level IS NULL OR a.level = :level) "
            + "AND (:label IS NULL OR (:label = '' AND a.label IS NULL) OR a.label = :label)")
    Page<ReturnRiskAssessment> search(@Param("level") String level, @Param("label") String label, Pageable pageable);
}
