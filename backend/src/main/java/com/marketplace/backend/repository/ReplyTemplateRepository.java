package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReplyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {

    List<ReplyTemplate> findByShopIdAndKindOrderByIdAsc(Long shopId, ReplyTemplate.Kind kind);

    long countByShopIdAndKind(Long shopId, ReplyTemplate.Kind kind);
}
