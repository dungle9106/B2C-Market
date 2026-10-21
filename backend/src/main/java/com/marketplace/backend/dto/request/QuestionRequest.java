package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionRequest {
    @NotNull
    private Long productId;

    @NotBlank(message = "Vui lòng nhập câu hỏi")
    @Size(max = 1000, message = "Câu hỏi tối đa 1000 ký tự")
    private String question;
}
