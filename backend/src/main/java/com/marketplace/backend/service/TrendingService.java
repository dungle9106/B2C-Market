package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ProductSearchCriteria;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.SearchKeywordDailyRepository;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * "Tu khoa hot" under the search box and "Tim kiem hang dau" on the home page, from what buyers searched in the last
 * 7 days. Only searches that found products count, and a keyword is shown only if it passes the same banned-word check
 * as listings, so nobody can push junk or abuse onto the home page by searching it many times.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int WINDOW_DAYS = 7;
    private static final long CACHE_MS = 5 * 60_000;

    private final SearchKeywordDailyRepository keywordRepository;
    private final CategoryRepository categoryRepository;
    private final ProductModerationService moderationService;
    private final ProductService productService;

    private record Cached(long at, List<Map<String, Object>> value) {}

    private volatile Cached hot;
    private volatile Cached top;

    /** A keyword search (first page) that found results. Never fails the search. */
    @Transactional
    public void record(String q, long results) {
        if (q == null || results <= 0) return;
        String display = q.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String key = TextNormalizer.normalize(display);
        if (key == null || key.length() < 2 || display.length() > 60) return;
        try {
            keywordRepository.record(LocalDate.now(VIETNAM), key, display);
        } catch (Exception e) {
            log.debug("Could not record search keyword: {}", e.getMessage());
        }
    }

    /** [{ keyword, searches }]: the week's most searched keywords, topped up with category names on a quiet site. */
    public List<Map<String, Object>> hotKeywords(int limit) {
        Cached c = hot;
        if (c == null || System.currentTimeMillis() - c.at() > CACHE_MS) {
            List<Map<String, Object>> list = new ArrayList<>(ranked(20));
            Set<String> seen = new HashSet<>();
            list.forEach(m -> seen.add(TextNormalizer.normalize((String) m.get("keyword"))));
            for (var cat : categoryRepository.findAll()) {
                if (list.size() >= 20) break;
                if (cat.getParent() == null || !cat.isActive()) continue;
                String name = cat.getName().toLowerCase(Locale.ROOT);
                if (seen.add(TextNormalizer.normalize(name))) list.add(entry(name, 0));
            }
            hot = c = new Cached(System.currentTimeMillis(), List.copyOf(list));
        }
        return c.value().subList(0, Math.min(Math.max(limit, 1), c.value().size()));
    }

    /** [{ keyword, searches, imageUrl, soldCount }]: the top keywords with the best-selling product each finds. */
    public List<Map<String, Object>> topSearches(int limit) {
        Cached c = top;
        if (c == null || System.currentTimeMillis() - c.at() > CACHE_MS) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> k : ranked(20)) {
                if (list.size() >= 12) break;
                ProductSearchCriteria criteria = new ProductSearchCriteria();
                criteria.setQ((String) k.get("keyword"));
                criteria.setSort("bestselling");
                criteria.setSize(1);
                List<ProductResponse> found = productService.search(criteria).getItems();
                if (found.isEmpty()) continue;
                Map<String, Object> m = new LinkedHashMap<>(k);
                m.put("imageUrl", found.get(0).getThumbnailUrl());
                m.put("soldCount", found.get(0).getSoldCount());
                list.add(m);
            }
            top = c = new Cached(System.currentTimeMillis(), List.copyOf(list));
        }
        return c.value().subList(0, Math.min(Math.max(limit, 1), c.value().size()));
    }

    private List<Map<String, Object>> ranked(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : keywordRepository.top(LocalDate.now(VIETNAM).minusDays(WINDOW_DAYS - 1), limit * 2)) {
            String display = (String) row[1];
            if (moderationService.textProblem(display) != null) continue;
            out.add(entry(display, ((Number) row[2]).longValue()));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static Map<String, Object> entry(String keyword, long searches) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("keyword", keyword);
        m.put("searches", searches);
        return m;
    }
}
