package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.BusinessType;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.service.ShippingService;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// The seller's own view of their shop (/api/seller/shop). Buyers get ShopPublicResponse.
@Data
public class ShopResponse {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;
    // only ever returned to the shop's own seller, so the settings form can
    // round-trip it instead of wiping it on save
    private String pickupAddress;
    private String pickupPhone;
    private String pickupDistrict;
    private String pickupWard;
    private String province;
    private boolean active;
    // moderation: PENDING / APPROVED / REJECTED / SUSPENDED, and the admin's reason
    private String status;
    private String statusReason;
    private LocalDateTime createdAt;

    // policies
    private Integer defaultReturnDays;
    private String returnConditions;
    private List<String> carriers;
    private Integer handlingDays;
    private BigDecimal freeShipMinOrder;

    // verification
    private boolean verified;
    private LocalDateTime verifiedAt;
    private BusinessType businessType;

    public static ShopResponse from(Shop shop) {
        ShopResponse r = new ShopResponse();
        r.setId(shop.getId());
        r.setName(shop.getName());
        r.setDescription(shop.getDescription());
        r.setLogoUrl(shop.getLogoUrl());
        r.setCoverUrl(shop.getCoverUrl());
        r.setPickupAddress(shop.getPickupAddress());
        r.setPickupPhone(shop.getPickupPhone());
        r.setPickupDistrict(shop.getPickupDistrict());
        r.setPickupWard(shop.getPickupWard());
        r.setProvince(shop.getProvince());
        r.setActive(shop.isActive());
        r.setStatus(shop.getStatus().name());
        r.setStatusReason(shop.getStatusReason());
        r.setCreatedAt(shop.getCreatedAt());
        r.setDefaultReturnDays(shop.getDefaultReturnDays());
        r.setReturnConditions(shop.getReturnConditions());
        r.setCarriers(ShippingService.carrierCodes(shop.getCarriers()));
        r.setHandlingDays(shop.getHandlingDays());
        r.setFreeShipMinOrder(shop.getFreeShipMinOrder());
        r.setVerified(shop.isVerified());
        r.setVerifiedAt(shop.getVerifiedAt());
        r.setBusinessType(shop.getBusinessType());
        return r;
    }
}
