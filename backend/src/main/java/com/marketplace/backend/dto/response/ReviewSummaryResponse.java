package com.marketplace.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

// Header of the reviews block: average, total, and counts for each filter button.
@Data
@AllArgsConstructor
public class ReviewSummaryResponse {
    private double average;
    private long total;
    private Map<Integer, Long> countByStars; // 5 -> 12, 4 -> 3, ...
    private long withMedia;
}
