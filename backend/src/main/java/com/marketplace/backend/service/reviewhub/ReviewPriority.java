package com.marketplace.backend.service.reviewhub;

import com.marketplace.backend.entity.ReviewHubItem;
import com.marketplace.backend.entity.ReviewHubItem.Level;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How urgently a review needs an answer (0-100), with the reasons in plain words and a reply deadline.
 * A bad review left unanswered is what drags a shop down: low stars, alarming words (fake, allergy, scam...),
 * safety or "missing item" issues, photos that every visitor sees, and the hours already gone by all raise it.
 */
public final class ReviewPriority {

    private ReviewPriority() {
    }

    // hours to answer, per level
    static final int URGENT_HOURS = 12, HIGH_HOURS = 24, NORMAL_HOURS = 72;
    private static final Set<String> SERIOUS = Set.of("AUTHENTICITY", "MISSING_ITEM", "WRONG_DESCRIPTION");

    public static void apply(ReviewHubItem i, List<String> severeHits, LocalDateTime now) {
        int score = 0;
        List<String> why = new ArrayList<>();
        switch (i.getRating()) {
            case 1 -> { score += 40; why.add("Đánh giá 1 sao"); }
            case 2 -> { score += 30; why.add("Đánh giá 2 sao"); }
            case 3 -> { score += 12; why.add("Đánh giá 3 sao"); }
            default -> { }
        }
        if (i.getSentiment() == ReviewHubItem.Sentiment.NEGATIVE && i.getRating() >= 3) {
            score += 15;
            why.add("Nội dung tiêu cực dù " + i.getRating() + " sao");
        }
        List<String> issues = i.getIssues() == null || i.getIssues().isBlank() ? List.of() : List.of(i.getIssues().split(","));
        if (issues.contains("AUTHENTICITY")) {
            score += 25;
            why.add("Nghi hàng giả / vấn đề an toàn");
        } else if (issues.stream().anyMatch(SERIOUS::contains)) {
            score += 12;
            why.add("Lỗi nghiêm trọng: " + issues.stream().filter(SERIOUS::contains).map(ReviewTextAnalyzer::label).findFirst().orElse(""));
        }
        if (severeHits != null && !severeHits.isEmpty()) {
            score += Math.min(30, 15 * severeHits.size());
            why.add("Có từ ngữ cảnh báo: \"" + String.join("\", \"", severeHits.subList(0, Math.min(3, severeHits.size()))) + "\"");
        }
        if (i.getMediaCount() > 0 && i.getRating() <= 3) {
            score += 8;
            why.add("Kèm ảnh/video, người xem dễ chú ý");
        }
        if (i.getContent() != null && i.getContent().length() > 200 && i.getRating() <= 2) {
            score += 5;
            why.add("Đánh giá dài, chi tiết");
        }
        if (i.getStatus() == ReviewHubItem.Status.NEW || i.getStatus() == ReviewHubItem.Status.DRAFTED) {
            long hours = Math.max(0, Duration.between(i.getReviewedAt(), now).toHours());
            if (i.getRating() <= 3 && hours >= 12) {
                score += Math.min(15, (int) (hours / 6));
                why.add("Chưa trả lời sau " + (hours >= 48 ? hours / 24 + " ngày" : hours + " giờ"));
            }
        }
        if (i.getRating() >= 4 && i.getSentiment() == ReviewHubItem.Sentiment.POSITIVE) {
            score = Math.min(score, 15);
            if (why.isEmpty()) why.add("Đánh giá tích cực: cảm ơn khách để giữ chân");
        }
        score = Math.min(100, score);
        Level level = score >= 70 ? Level.URGENT : score >= 45 ? Level.HIGH : score >= 20 ? Level.NORMAL : Level.LOW;
        i.setPriority(score);
        i.setPriorityLevel(level);
        i.setPriorityReasons(ReviewTextAnalyzer.cut(String.join(" | ", why), 500));
        int hoursToAnswer = switch (level) {
            case URGENT -> URGENT_HOURS;
            case HIGH -> HIGH_HOURS;
            case NORMAL -> NORMAL_HOURS;
            case LOW -> 7 * 24;
        };
        i.setReplyDueAt(i.getReviewedAt().plusHours(hoursToAnswer));
    }
}
