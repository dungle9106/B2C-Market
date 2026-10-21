package com.marketplace.backend.repository;

import com.marketplace.backend.entity.FlashSaleRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashSaleRegistrationRepository extends JpaRepository<FlashSaleRegistration, Long> {

    @Query("SELECT r FROM FlashSaleRegistration r JOIN FETCH r.product p JOIN FETCH r.shop WHERE r.event.id = :eventId "
            + "AND (:status IS NULL OR r.status = :status) ORDER BY r.id")
    List<FlashSaleRegistration> forEvent(@Param("eventId") Long eventId, @Param("status") FlashSaleRegistration.Status status);

    Optional<FlashSaleRegistration> findByEventIdAndProductId(Long eventId, Long productId);

    List<FlashSaleRegistration> findByShopIdOrderByIdDesc(Long shopId);

    @Query("SELECT COUNT(r) FROM FlashSaleRegistration r WHERE r.event.id = :eventId AND r.shop.id = :shopId AND r.status IN :statuses")
    long countForShop(@Param("eventId") Long eventId, @Param("shopId") Long shopId,
                      @Param("statuses") Collection<FlashSaleRegistration.Status> statuses);

    // the product is already in another live slot overlapping [start, end)
    @Query("SELECT COUNT(r) > 0 FROM FlashSaleRegistration r WHERE r.product.id = :productId AND r.event.id <> :eventId "
            + "AND r.status IN :statuses AND r.event.cancelled = false AND r.event.startAt < :end AND r.event.endAt > :start")
    boolean inOverlappingEvent(@Param("productId") Long productId, @Param("eventId") Long eventId,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                               @Param("statuses") Collection<FlashSaleRegistration.Status> statuses);

    // [eventId, status, count]
    @Query("SELECT r.event.id, r.status, COUNT(r) FROM FlashSaleRegistration r GROUP BY r.event.id, r.status")
    List<Object[]> countsByEvent();

    long countByStatus(FlashSaleRegistration.Status status);

    // the product's approved slots that are not over yet, earliest first
    @Query("SELECT r FROM FlashSaleRegistration r JOIN FETCH r.event e WHERE r.product.id = :productId AND r.status = :approved "
            + "AND e.cancelled = false AND e.endAt > :now ORDER BY e.startAt")
    List<FlashSaleRegistration> approvedAhead(@Param("productId") Long productId, @Param("now") LocalDateTime now,
                                              @Param("approved") FlashSaleRegistration.Status approved);

    // approved products of slots running now or starting within the next minutes
    @Query("SELECT r FROM FlashSaleRegistration r JOIN FETCH r.event e JOIN FETCH r.product WHERE r.status = :approved "
            + "AND e.cancelled = false AND e.startAt <= :soon AND e.endAt > :now")
    List<FlashSaleRegistration> due(@Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon,
                                    @Param("approved") FlashSaleRegistration.Status approved);
}
