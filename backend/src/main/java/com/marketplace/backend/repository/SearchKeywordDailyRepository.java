package com.marketplace.backend.repository;

import com.marketplace.backend.entity.SearchKeywordDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SearchKeywordDailyRepository extends JpaRepository<SearchKeywordDaily, SearchKeywordDaily.Key> {

    @Modifying
    @Query(value = "INSERT INTO search_keyword_daily (day, keyword, display, count) VALUES (:day, :keyword, :display, 1) "
            + "ON CONFLICT (day, keyword) DO UPDATE SET count = search_keyword_daily.count + 1, display = :display", nativeQuery = true)
    void record(@Param("day") LocalDate day, @Param("keyword") String keyword, @Param("display") String display);

    // [keyword, display, searches] since a day, most searched first
    @Query(value = "SELECT keyword, MAX(display), SUM(count) AS total FROM search_keyword_daily WHERE day >= :since "
            + "GROUP BY keyword ORDER BY total DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> top(@Param("since") LocalDate since, @Param("limit") int limit);
}
