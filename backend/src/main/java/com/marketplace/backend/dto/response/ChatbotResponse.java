package com.marketplace.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// Buyer chatbot answer: the text plus what it was based on, so the UI can show
// clickable product / order cards under the bubble.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponse {
    private String reply;
    private List<ProductResponse> products;
    private List<OrderRef> orders;
    // the bot couldn't help or the buyer asked for a human: offer "Gui yeu cau ho tro"
    private boolean suggestSupport;
    private LocalDateTime createdAt;

    public record OrderRef(Long id, String code, String status, String shopName) {}
}
