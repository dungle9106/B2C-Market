package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

/** How often a keyword was searched (with results) on one day: the source of "Tu khoa hot" and "Tim kiem hang dau". */
@Entity
@Table(name = "search_keyword_daily")
@IdClass(SearchKeywordDaily.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchKeywordDaily {

    @Id
    @Column(nullable = false)
    private LocalDate day;

    @Id
    @Column(nullable = false, length = 60)
    private String keyword;        // normalised: lower case, no accents

    @Column(nullable = false, length = 60)
    private String display;        // as a buyer typed it, accents kept

    @Column(nullable = false)
    private long count;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private LocalDate day;
        private String keyword;
    }
}
