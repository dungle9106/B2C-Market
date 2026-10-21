package com.marketplace.backend.entity;

import com.marketplace.backend.util.TextNormalizer;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// DynamicUpdate: stock is also moved by atomic UPDATE statements (StockService); an entity
// update must only write the columns it changed, never an old stock value
@Entity
@DynamicUpdate
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    // seller's own stock-keeping code, unique within the shop (also the key of an Excel import row)
    @Column(length = 50)
    private String sku;

    // Shopee-style variation groups, e.g. "Màu sắc" x "Kích cỡ"; null = flat variant names
    @Column(length = 30)
    private String tier1Name;

    @Column(length = 30)
    private String tier2Name;

    // the shop's own categories ("Danh muc cua Shop"), separate from the platform categories
    @ManyToMany
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JoinTable(name = "product_shop_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "shop_category_id"))
    @Builder.Default
    private Set<ShopCategory> shopCategories = new HashSet<>();

    // lower-cased, accent-stripped copy of the name ("ao thun nam") so search
    // and autocomplete match whether or not the buyer types Vietnamese accents
    @Column(length = 600)
    private String searchText;

    @Column(length = 4000)
    private String description;

    // regular selling price (before any flash sale)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // optional "list price" shown crossed out next to the price
    @Column(precision = 12, scale = 2)
    private BigDecimal originalPrice;

    // flash sale: flashSalePrice applies only between start and end
    @Column(precision = 12, scale = 2)
    private BigDecimal flashSalePrice;
    private LocalDateTime flashSaleStart;
    private LocalDateTime flashSaleEnd;
    // promo quantity at the flash price (null = the whole stock) and how many went at that price
    private Integer flashSaleStock;
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer flashSaleSold = 0;
    // max units one buyer may get at the flash price during this flash sale (null = no limit)
    private Integer flashSaleMaxPerBuyer;
    // set when the flash sale comes from a Shoppi Flash Sale slot (FlashSaleEvent), null for the shop's own
    private Long flashSaleEventId;

    @Builder.Default
    private Integer stock = 0;

    @Builder.Default
    private Integer soldCount = 0;

    // cover image; always the first of imageUrls when images are set
    private String thumbnailUrl;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Column(length = 1000)
    private String videoUrl;

    // column defaults so ddl-auto=update can add these to a table that already has rows
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean freeShipping = false;

    // days the buyer can return the item; 0 = no returns
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 7")
    private Integer returnDays = 7;

    // denormalised from reviews so listings can filter/sort by rating cheaply
    @Builder.Default
    @Column(nullable = false, columnDefinition = "double precision default 0")
    private Double ratingAvg = 0.0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer ratingCount = 0;

    // visible to buyers: the seller's show/hide switch, forced off unless moderation is APPROVED
    @Builder.Default
    private boolean active = true;

    // ==== content moderation ("Kiem duyet san pham") ====
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'APPROVED'")
    private ProductModeration moderationStatus = ProductModeration.APPROVED;

    // why it was rejected / removed / held (shown to the seller)
    @Column(length = 500)
    private String moderationReason;

    // REMOVED products: which policy was broken (ProductModerationService.VIOLATIONS)
    @Column(length = 40)
    private String violationType;

    // automatic content checks: "CODE|severity|label" separated by ";" (ProductModerationService)
    @Column(length = 1000)
    private String moderationFlags;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer riskScore = 0;

    private LocalDateTime moderatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.searchText = TextNormalizer.normalize(name);
    }

    @PreUpdate
    public void preUpdate() {
        this.searchText = TextNormalizer.normalize(name);
    }

    public boolean isFlashSaleActive() {
        LocalDateTime now = LocalDateTime.now();
        return flashSalePrice != null && flashSaleStart != null && flashSaleEnd != null
                && !now.isBefore(flashSaleStart) && now.isBefore(flashSaleEnd)
                && (flashSaleStock == null || flashSaleSold == null || flashSaleSold < flashSaleStock);
    }

    // what the buyer actually pays right now for the base product (variants add their extraPrice)
    public BigDecimal getEffectivePrice() {
        return isFlashSaleActive() ? flashSalePrice : price;
    }

    public BigDecimal unitPriceFor(ProductVariant variant) {
        BigDecimal unit = getEffectivePrice();
        if (variant != null && variant.getExtraPrice() != null) {
            unit = unit.add(variant.getExtraPrice());
        }
        return unit;
    }

    // price shown crossed out: the list price, or the regular price during a flash sale
    public BigDecimal getDisplayOriginalPrice() {
        BigDecimal effective = getEffectivePrice();
        BigDecimal candidate = originalPrice;
        if (isFlashSaleActive() && (candidate == null || price.compareTo(candidate) > 0)) {
            candidate = price;
        }
        return candidate != null && candidate.compareTo(effective) > 0 ? candidate : null;
    }

    public boolean isOnSale() {
        return getDisplayOriginalPrice() != null;
    }
}
