package com.marketplace.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductRequest {
    @NotBlank(message = "Vui lòng nhập tên sản phẩm")
    @Size(max = 120, message = "Tên sản phẩm tối đa 120 ký tự")
    private String name;

    @Size(max = 50, message = "SKU tối đa 50 ký tự")
    @Pattern(regexp = "^$|^[A-Za-z0-9._\\-]+$", message = "SKU chỉ gồm chữ, số và . _ -")
    private String sku;

    @Size(max = 4000, message = "Mô tả tối đa 4000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng nhập giá")
    @Positive(message = "Giá phải lớn hơn 0")
    private BigDecimal price;

    @Positive(message = "Giá gốc phải lớn hơn 0")
    private BigDecimal originalPrice;

    @PositiveOrZero
    private Integer stock;

    private Long categoryId;

    // the shop's own categories (Danh muc cua Shop)
    @Size(max = 10, message = "Tối đa 10 danh mục của shop")
    private List<Long> shopCategoryIds = new ArrayList<>();

    @Size(max = 9, message = "Tối đa 9 hình ảnh")
    private List<String> imageUrls = new ArrayList<>();
    private String videoUrl;

    private boolean freeShipping;

    @Min(value = 0, message = "Số ngày đổi trả không hợp lệ")
    @Max(value = 30, message = "Đổi trả tối đa 30 ngày")
    private Integer returnDays;

    // flash sale: all three set, or none
    @Positive(message = "Giá flash sale phải lớn hơn 0")
    private BigDecimal flashSalePrice;
    private LocalDateTime flashSaleStart;
    private LocalDateTime flashSaleEnd;
    @Positive(message = "Số suất Flash Sale phải lớn hơn 0")
    private Integer flashSaleStock;          // null = the whole stock
    @Positive(message = "Giới hạn mỗi khách phải lớn hơn 0")
    private Integer flashSaleMaxPerBuyer;    // null = no limit

    // variation groups ("Màu sắc", "Kích cỡ"); when set, each variant gives option1 (and option2)
    // instead of a free-text name. Both empty = flat variant names (older products).
    @Size(max = 30, message = "Tên nhóm phân loại tối đa 30 ký tự")
    private String tier1Name;

    @Size(max = 30, message = "Tên nhóm phân loại tối đa 30 ký tự")
    private String tier2Name;

    @Valid
    @Size(max = 100, message = "Tối đa 100 phân loại")
    private List<VariantRequest> variants = new ArrayList<>();

    @Data
    public static class VariantRequest {
        private Long id; // existing variant when editing, null for a new one

        // required when the product has no tiers (checked in ProductService)
        @Size(max = 60)
        private String name;

        @Size(max = 40, message = "Giá trị phân loại tối đa 40 ký tự")
        private String option1;

        @Size(max = 40, message = "Giá trị phân loại tối đa 40 ký tự")
        private String option2;

        @Size(max = 50, message = "SKU tối đa 50 ký tự")
        private String sku;

        private String imageUrl;

        @PositiveOrZero(message = "Giá cộng thêm không hợp lệ")
        private BigDecimal extraPrice;

        @NotNull(message = "Vui lòng nhập kho cho phân loại")
        @PositiveOrZero(message = "Kho không hợp lệ")
        private Integer stock;
    }
}
