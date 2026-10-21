package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.entity.VoucherScope;
import com.marketplace.backend.entity.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VoucherResponse {
    private Long id;
    private String code;
    private String title;           // "Giam 10% toi da 50k"
    private VoucherScope scope;
    private Long shopId;
    private String shopName;
    private VoucherType type;
    private BigDecimal value;
    private BigDecimal maxDiscount;
    private BigDecimal minOrder;
    private Integer usageLimit;
    private Integer usedCount;
    private Integer perUserLimit;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean active;
    private Integer usedByMe;       // buyer listings only
    private boolean publicVisible;
    private List<Long> productIds;  // empty = whole shop
    // seller / admin statistics (orders not cancelled)
    private Long ordersCount;
    private BigDecimal discountGiven;
    private BigDecimal orderValue;
    // Shoppi Vouchers
    private List<Long> categoryIds;
    private List<String> categoryNames;
    private boolean newBuyerOnly;
    private BigDecimal budget;
    private BigDecimal spent;          // discounts funded so far (admin)
    private Long buyersCount;          // distinct buyers (admin)
    private Long campaignId;
    private String campaignName;
    private String campaignSlug;

    public static VoucherResponse from(Voucher v) {
        VoucherResponse r = new VoucherResponse();
        r.setId(v.getId());
        r.setCode(v.getCode());
        r.setTitle(v.title());
        r.setScope(v.getScope());
        if (v.getShop() != null) {
            r.setShopId(v.getShop().getId());
            r.setShopName(v.getShop().getName());
        }
        r.setType(v.getType());
        r.setValue(v.getDiscountValue());
        r.setMaxDiscount(v.getMaxDiscountAmount());
        r.setMinOrder(v.getMinOrderAmount());
        r.setUsageLimit(v.getUsageLimit());
        r.setUsedCount(v.getUsedCount());
        r.setPerUserLimit(v.getPerUserLimit());
        r.setStartAt(v.getStartAt());
        r.setEndAt(v.getEndAt());
        r.setActive(v.isActive());
        r.setPublicVisible(v.isPublicVisible());
        r.setProductIds(v.getProducts().stream().map(p -> p.getId()).toList());
        r.setCategoryIds(v.getCategoryIds().stream().sorted().toList());
        r.setNewBuyerOnly(v.isNewBuyerOnly());
        r.setBudget(v.getBudget());
        if (v.getCampaign() != null) {
            r.setCampaignId(v.getCampaign().getId());
            r.setCampaignName(v.getCampaign().getName());
            r.setCampaignSlug(v.getCampaign().getSlug());
        }
        return r;
    }
}
