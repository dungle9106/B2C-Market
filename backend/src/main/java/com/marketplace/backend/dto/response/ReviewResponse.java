package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewResponse {
    private Long id;
    private String buyerName;      // masked, e.g. "n*****h"
    private Integer rating;
    private String comment;
    private List<String> imageUrls;
    private String videoUrl;
    private String variantName;
    private String sellerReply;
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review r) {
        ReviewResponse res = new ReviewResponse();
        res.setId(r.getId());
        res.setBuyerName(mask(r.getBuyer().getFullName()));
        res.setRating(r.getRating());
        res.setComment(r.getComment());
        res.setImageUrls(List.copyOf(r.getImageUrls()));
        res.setVideoUrl(r.getVideoUrl());
        res.setVariantName(r.getOrderItem() != null ? r.getOrderItem().getVariantNameSnapshot() : null);
        res.setSellerReply(r.getSellerReply());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }

    // like Shopee: show only the first and last character of the reviewer's name
    static String mask(String name) {
        if (name == null || name.isBlank()) return "Người dùng Shoppi";
        String n = name.trim();
        if (n.length() <= 2) return n.charAt(0) + "*";
        return n.charAt(0) + "*****" + n.charAt(n.length() - 1);
    }
}
