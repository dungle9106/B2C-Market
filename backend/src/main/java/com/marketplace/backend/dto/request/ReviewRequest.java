package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewRequest {
    // the delivered order line being reviewed (proves a real purchase)
    @NotNull
    private Long orderItemId;

    @NotNull @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
    private String comment;

    @Size(max = 6, message = "Tối đa 6 hình ảnh")
    private List<String> imageUrls = new ArrayList<>();

    private String videoUrl;

    // rating of the shop's service and of the delivery; kept once per order (first review wins)
    @Min(1) @Max(5)
    private Integer shopRating;

    @Min(1) @Max(5)
    private Integer shippingRating;
}
