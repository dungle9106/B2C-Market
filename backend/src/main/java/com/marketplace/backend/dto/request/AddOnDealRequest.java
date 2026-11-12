package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.AddOnDeal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AddOnDealRequest {

    @NotBlank(message = "Vui lòng nhập tên chương trình")
    @Size(min = 3, max = 100, message = "Tên chương trình từ 3 đến 100 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn loại chương trình")
    private AddOnDeal.Type type;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    private LocalDateTime endAt;

    private BigDecimal minSpend;          // GIFT only

    @Min(value = 1, message = "Giới hạn mua kèm từ 1 đến 10")
    @Max(value = 10, message = "Giới hạn mua kèm từ 1 đến 10")
    private Integer purchaseLimit;

    @NotEmpty(message = "Vui lòng chọn sản phẩm chính")
    @Size(max = 100, message = "Tối đa 100 sản phẩm chính")
    private List<Long> mainProductIds = new ArrayList<>();

    @NotEmpty(message = "Vui lòng chọn sản phẩm mua kèm / quà tặng")
    @Size(max = 20, message = "Tối đa 20 sản phẩm mua kèm / quà tặng")
    @Valid
    private List<ItemRequest> items = new ArrayList<>();

    @Data
    public static class ItemRequest {
        @NotNull
        private Long productId;
        private BigDecimal dealPrice;     // ADDON: required, below the product's price; GIFT: ignored
    }
}
