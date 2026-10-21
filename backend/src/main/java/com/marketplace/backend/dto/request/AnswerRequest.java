package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerRequest {
    @NotBlank(message = "Vui lòng nhập câu trả lời")
    @Size(max = 2000, message = "Câu trả lời tối đa 2000 ký tự")
    private String answer;
}
