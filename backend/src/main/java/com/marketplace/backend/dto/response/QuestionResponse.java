package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.ProductQuestion;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionResponse {
    private Long id;
    private Long productId;
    private String productName;    // used by the seller's Q&A page
    private String askerName;      // masked like review names
    private String question;
    private String answer;
    private LocalDateTime answeredAt;
    private LocalDateTime answerUpdatedAt;
    // seller only
    private String askerFullName;
    private String productThumbnailUrl;
    private boolean hidden;
    private LocalDateTime createdAt;

    public static QuestionResponse from(ProductQuestion q) {
        QuestionResponse r = new QuestionResponse();
        r.setId(q.getId());
        r.setProductId(q.getProduct().getId());
        r.setProductName(q.getProduct().getName());
        r.setAskerName(ReviewResponse.mask(q.getAsker().getFullName()));
        r.setQuestion(q.getQuestion());
        r.setAnswer(q.getAnswer());
        r.setAnsweredAt(q.getAnsweredAt());
        r.setCreatedAt(q.getCreatedAt());
        r.setAnswerUpdatedAt(q.getAnswerUpdatedAt());
        return r;
    }

    // the shop sees who asked and the product picture
    public static QuestionResponse forSeller(ProductQuestion q) {
        QuestionResponse r = from(q);
        r.setAskerFullName(q.getAsker().getFullName());
        r.setProductThumbnailUrl(q.getProduct().getThumbnailUrl());
        r.setHidden(q.isHidden());
        return r;
    }
}
