package com.marketplace.backend.service.reviewhub;

/** Published by ReviewService so the review hub follows the reviews written and answered on Shoppi. */
public final class ReviewHubEvents {

    private ReviewHubEvents() {
    }

    public record ReviewWritten(Long reviewId) {
    }

    public record ReviewReplied(Long reviewId, String reply) {
    }
}
