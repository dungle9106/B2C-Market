package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AddToCartRequest {

    @NotNull
    private Long productId;

    private Long variantId; // nullable

    @NotNull @Positive
    private Integer quantity;
}
