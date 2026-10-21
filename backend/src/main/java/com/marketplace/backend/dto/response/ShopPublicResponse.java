package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.BusinessType;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.service.ShippingService;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// What buyers see of a shop: the shop page and the policy box on product pages.
// No pickup address / phone, no legal documents.
@Data
public class ShopPublicResponse {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String province;
    private boolean active;
    private LocalDateTime joinedAt;
    private boolean verified;
    private BusinessType businessType; // only when verified
    private long productCount;
    private Double ratingAvg;          // buyers' shop-service stars
    private long ratingCount;
    // chat, last 30 days (null = not enough conversations yet)
    private Double chatResponseRate;
    private String chatResponseTime;

    // policies
    private Integer defaultReturnDays;
    private String returnConditions;
    private List<String> carrierNames;
    private Integer handlingDays;
    private BigDecimal freeShipMinOrder;

    public static ShopPublicResponse from(Shop s, long productCount, Double ratingAvg, long ratingCount) {
        ShopPublicResponse r = new ShopPublicResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setLogoUrl(s.getLogoUrl());
        r.setCoverUrl(s.getCoverUrl());
        r.setProvince(s.getProvince());
        r.setActive(s.isActive());
        r.setJoinedAt(s.getCreatedAt());
        r.setVerified(s.isVerified());
        r.setBusinessType(s.isVerified() ? s.getBusinessType() : null);
        r.setProductCount(productCount);
        r.setRatingAvg(ratingAvg);
        r.setRatingCount(ratingCount);
        r.setDefaultReturnDays(s.getDefaultReturnDays());
        r.setReturnConditions(s.getReturnConditions());
        r.setCarrierNames(ShippingService.carrierCodes(s.getCarriers()).stream().map(ShippingService::carrierName).toList());
        r.setHandlingDays(s.getHandlingDays());
        r.setFreeShipMinOrder(s.getFreeShipMinOrder());
        return r;
    }
}
