package com.marketplace.backend.service.reco;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Gợi ý cho bạn" and "Sản phẩm tương tự", learned from what buyers do (app/recommender.py of the AI service):
 *
 *   view 1 · like 2 · add to cart 3 · purchase 5 · good review (4-5 stars) +2 · bad review (1-2 stars) -3
 *
 * The model is retrained every night (and from the admin page). Until it is trained, or when the AI service is
 * down, the old rules answer: same category as the recently viewed products, then best sellers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int LIMIT = 12;

    private final RecommendationModelClient model;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbc;

    // every signal of interest: (user, product, weight, epoch seconds)
    private static final String INTERACTIONS = """
            select user_id as u, product_id as i, 1.0 as w, extract(epoch from viewed_at) as ts from view_history
            union all select user_id, product_id, 3.0, extract(epoch from created_at) from product_events
                where type = 'ADD_TO_CART' and user_id is not null
            union all select user_id, product_id, 2.0, extract(epoch from created_at) from product_likes
            union all select o.buyer_id, oi.product_id, 5.0, extract(epoch from o.created_at)
                from order_items oi join orders o on o.id = oi.order_id where o.status <> 'CANCELLED'
            union all select buyer_id, product_id, case when rating >= 4 then 2.0 when rating <= 2 then -3.0 else 0 end,
                extract(epoch from created_at) from reviews
            """;

    /** One recommendation; because = the product the buyer looked at that led to it (null when unknown). */
    public record Pick(ProductResponse product, String because, boolean learned) {}

    // ==================== for the shop ====================

    /** "Gợi ý cho bạn" of the home page. */
    @Transactional(readOnly = true)
    public List<ProductResponse> forUser(Long userId) {
        return picks(userId, LIMIT).stream().map(Pick::product).toList();
    }

    /** The same, with the reason of each pick (the shopping assistant tells the buyer why). */
    @Transactional(readOnly = true)
    public List<Pick> picks(Long userId, int k) {
        List<Map<String, Object>> history = history(userId);
        if (!history.isEmpty()) {
            Optional<List<RecommendationModelClient.Scored>> scored = model.recommend(history, List.of(), k * 2);
            if (scored.isPresent() && !scored.get().isEmpty()) {
                Map<Long, Product> products = load(scored.get().stream().map(RecommendationModelClient.Scored::item).toList());
                Set<Long> because = scored.get().stream().map(RecommendationModelClient.Scored::because).filter(Objects::nonNull).collect(Collectors.toSet());
                Map<Long, String> names = productRepository.findAllById(because).stream().collect(Collectors.toMap(Product::getId, Product::getName));
                List<Pick> out = scored.get().stream()
                        .filter(s -> products.containsKey(s.item()))
                        .map(s -> new Pick(ProductResponse.from(products.get(s.item())), s.because() == null ? null : names.get(s.because()), true))
                        .limit(k).collect(Collectors.toCollection(ArrayList::new));
                if (out.size() >= Math.min(k, 4)) return out;
            }
        }
        return rulePicks(userId, k);
    }

    /** "Sản phẩm tương tự": bought / viewed by the same people, otherwise the same category. */
    @Transactional(readOnly = true)
    public List<ProductResponse> similar(Product product) {
        List<ProductResponse> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>(List.of(product.getId()));
        model.similar(product.getId(), LIMIT * 2).ifPresent(scored -> {
            Map<Long, Product> products = load(scored.stream().map(RecommendationModelClient.Scored::item).toList());
            scored.stream().map(s -> products.get(s.item())).filter(Objects::nonNull).limit(LIMIT)
                    .forEach(p -> { out.add(ProductResponse.from(p)); seen.add(p.getId()); });
        });
        if (out.size() < LIMIT && product.getCategory() != null) {
            productRepository.findSimilarByCategory(product.getCategory().getId(), product.getId()).stream()
                    .filter(p -> p.getShop().isActive() && seen.add(p.getId()))
                    .limit(LIMIT - out.size()).map(ProductResponse::from).forEach(out::add);
        }
        return out;
    }

    // the rules used before the model (and whenever it can't answer)
    private List<Pick> rulePicks(Long userId, int k) {
        LinkedHashMap<Long, Pick> out = new LinkedHashMap<>();
        List<Object[]> recent = jdbc.query("""
                select v.product_id, p.category_id, p.name from view_history v join products p on p.id = v.product_id
                where v.user_id = ? order by v.viewed_at desc limit 10""",
                (rs, n) -> new Object[]{rs.getLong(1), rs.getObject(2) == null ? null : rs.getLong(2), rs.getString(3)}, userId);
        Set<Long> viewed = recent.stream().map(r -> (Long) r[0]).collect(Collectors.toSet());
        for (Object[] r : recent) {
            if (r[1] == null) continue;
            productRepository.findSimilarByCategory((Long) r[1], (Long) r[0]).stream()
                    .filter(p -> p.getShop().isActive() && !viewed.contains(p.getId()))
                    .limit(4).forEach(p -> out.putIfAbsent(p.getId(), new Pick(ProductResponse.from(p), (String) r[2], false)));
            if (out.size() >= k) break;
        }
        if (out.size() < k) {
            productRepository.findTop10ByActiveTrueOrderBySoldCountDesc().stream()
                    .filter(p -> p.getShop().isActive())
                    .forEach(p -> out.putIfAbsent(p.getId(), new Pick(ProductResponse.from(p), null, false)));
        }
        return out.values().stream().limit(k).toList();
    }

    /** The buyer's 50 most recent products with their summed weight. */
    private List<Map<String, Object>> history(Long userId) {
        return jdbc.query("select i, sum(w) as w from (" + INTERACTIONS + ") x where u = ? group by i order by max(ts) desc limit 50",
                (rs, n) -> Map.<String, Object>of("item", rs.getLong("i"), "weight", rs.getDouble("w")), userId).stream()
                .filter(m -> ((Double) m.get("weight")) > 0).toList();
    }

    /** Active products of active shops by id. */
    private Map<Long, Product> load(Collection<Long> ids) {
        return productRepository.findAllById(ids).stream()
                .filter(p -> p.isActive() && p.getShop().isActive())
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    // ==================== training ====================

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(model.status());
        m.put("data", jdbc.queryForMap("select count(distinct u) as buyers, count(*) as events from (" + INTERACTIONS + ") x"));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> train() {
        List<Map<String, Object>> interactions = jdbc.query("select u, i, w, ts from (" + INTERACTIONS + ") x where w <> 0",
                (rs, n) -> Map.<String, Object>of("user", rs.getString("u"), "item", rs.getLong("i"), "weight", rs.getDouble("w"), "ts", rs.getDouble("ts")));
        // the category and its top-level parent: a little "same kind of product" knowledge for new products
        List<Map<String, Object>> items = jdbc.query("""
                select p.id, p.category_id, coalesce(c.parent_id, c.id) as parent
                from products p left join categories c on c.id = p.category_id where p.active""",
                (rs, n) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("category", rs.getObject("category_id") == null ? null : rs.getLong("category_id"));
                    m.put("parent", rs.getObject("parent") == null ? null : rs.getLong("parent"));
                    return m;
                });
        Map<String, Object> result = new LinkedHashMap<>(model.train(interactions, items));
        result.put("configured", true);
        log.info("Recommendation model trained: {} events, {} products", interactions.size(), items.size());
        return result;
    }

    // every night, when there is enough new behaviour to learn from; failures (too little data, service off) are fine
    @Scheduled(cron = "${app.reco.train-cron:0 40 3 * * *}")
    public void nightlyTrain() {
        if (!model.available()) return;
        try {
            train();
        } catch (Exception e) {
            log.info("Nightly recommendation training skipped: {}", e.getMessage());
        }
    }
}
