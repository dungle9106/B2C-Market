package com.marketplace.backend.service.search;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.service.AiOpsService;
import com.marketplace.backend.service.ai.AiSettings;
import com.marketplace.backend.service.ai.OpenAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Semantic product search ("tìm theo ý nghĩa"): products and queries are turned into embeddings (OpenAI
 * text-embedding-3-small, 256 numbers) and compared by cosine similarity, so "quà tặng cho mẹ" finds a scarf or a
 * handbag even when no word matches. Used by the search page and as the second retrieval step of the buyer chatbot
 * (RAG): keyword matches first, then the closest products in meaning.
 * Products are embedded in the background (new or changed text), only while AI is configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    public static final String MODEL = "text-embedding-3-small";
    private static final int BATCH = 64;
    private static final double MIN_SCORE = 0.25;

    private final EmbeddingStore store;
    private final OpenAiProvider openAi;
    private final AiOpsService ops;
    private final ProductRepository productRepository;

    // the last queries' vectors: typing the same search again costs nothing
    private final Map<String, float[]> queryCache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> e) {
            return size() > 500;
        }
    });
    private final Map<String, Object> lastRun = new ConcurrentHashMap<>();

    /** Usable now? (AI on, OpenAI configured: the self-hosted model has no embeddings) */
    public boolean available() {
        AiSettings s = ops.settings();
        return s.openaiReady() && ops.blockReason(s) == null;
    }

    // ==================== indexing ====================

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void indexPending() {
        if (!available()) return;
        int done = 0;
        for (int round = 0; round < 5; round++) {   // at most 320 products a minute
            List<Object[]> rows = store.stale(BATCH);
            if (rows.isEmpty()) break;
            List<String> texts = rows.stream().map(r -> text((String) r[1], (String) r[2], (String) r[3])).toList();
            OpenAiProvider.Embeddings e = openAi.embed(ops.settings(), MODEL, EmbeddingStore.DIMS, texts);
            if (e.error() != null) {
                lastRun.put("error", e.error());
                log.warn("Embedding failed: {}", e.error());
                break;
            }
            for (int i = 0; i < rows.size(); i++) store.save((Long) rows.get(i)[0], MODEL, (String) rows.get(i)[4], e.vectors().get(i));
            done += rows.size();
            lastRun.remove("error");
        }
        if (done > 0) {
            lastRun.put("embedded", done);
            lastRun.put("at", java.time.LocalDateTime.now().toString());
            log.info("Embedded {} products", done);
        }
    }

    static String text(String name, String category, String description) {
        String d = description == null ? "" : description.length() > 800 ? description.substring(0, 800) : description;
        return name + (category != null ? " | Danh mục: " + category : "") + (d.isBlank() ? "" : " | " + d);
    }

    // ==================== searching ====================

    /** Products closest in meaning to the text; empty when AI is unavailable. */
    @Transactional(readOnly = true)
    public List<ProductResponse> search(String q, int limit) {
        return nearestProducts(q, limit).stream().map(ProductResponse::from).toList();
    }

    /** For the chatbot (entities, best first). */
    @Transactional(readOnly = true)
    public List<Product> nearestProducts(String q, int limit) {
        if (q == null || q.isBlank() || !available()) return List.of();
        String key = q.trim().toLowerCase(Locale.ROOT);
        float[] v = queryCache.get(key);
        if (v == null) {
            OpenAiProvider.Embeddings e = openAi.embed(ops.settings(), MODEL, EmbeddingStore.DIMS, List.of(key));
            if (e.error() != null) return List.of();
            v = e.vectors().get(0);
            queryCache.put(key, v);
        }
        List<EmbeddingStore.Hit> hits = store.nearest(v, Math.min(Math.max(limit, 1), 50), MIN_SCORE);
        Map<Long, Product> byId = productRepository.findAllById(hits.stream().map(EmbeddingStore.Hit::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return hits.stream().map(h -> byId.get(h.productId())).filter(Objects::nonNull).toList();
    }

    // ==================== admin ====================

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(store.counts());
        m.put("engine", store.pgvector() ? "pgvector" : "real[] + cosine (Java)");
        m.put("model", MODEL);
        m.put("dimensions", EmbeddingStore.DIMS);
        m.put("available", available());
        m.putAll(lastRun);
        return m;
    }

    public Map<String, Object> reindex() {
        store.clear();
        queryCache.clear();
        return Map.of("ok", true, "message", "Đã xóa chỉ mục, hệ thống sẽ tạo lại trong vài phút");
    }
}
