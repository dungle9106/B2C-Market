package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.entity.VoucherScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCodeAndActiveTrue(String code);
    List<Voucher> findByShopId(Long shopId);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Voucher> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Voucher> findByScopeOrderByCreatedAtDesc(VoucherScope scope);

    // shown on the shop / product pages
    @Query("SELECT v FROM Voucher v WHERE v.shop.id = :shopId AND v.active = true AND v.publicVisible = true "
            + "AND v.startAt <= :now AND v.endAt > :now ORDER BY v.endAt ASC")
    List<Voucher> runningPublicOfShop(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    // candidates for the buyer's voucher picker; time/usage checks happen in VoucherService
    @Query("SELECT v FROM Voucher v WHERE v.active = true AND " +
           "(v.scope = com.marketplace.backend.entity.VoucherScope.PLATFORM OR v.shop.id IN :shopIds)")
    List<Voucher> findActiveForShops(@Param("shopIds") Collection<Long> shopIds);

    // Shoppi Vouchers of a promotion programme
    List<Voucher> findByCampaignIdOrderByStartAt(Long campaignId);

    long countByCampaignId(Long campaignId);
}
