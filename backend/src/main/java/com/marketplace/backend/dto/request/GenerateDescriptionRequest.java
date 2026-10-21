package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GenerateDescriptionRequest {
    @NotBlank(message = "Vui lòng nhập tên sản phẩm")
    @Size(max = 200)
    private String productName;

    private String material;   // chat lieu
    private String usage;      // cong dung
    private String targetUser; // doi tuong su dung

    @Size(max = 1000)
    private String highlights; // diem noi bat, one per line
    private Long categoryId;
    private String tone;       // friendly | professional | short
    private List<String> variants;
}
