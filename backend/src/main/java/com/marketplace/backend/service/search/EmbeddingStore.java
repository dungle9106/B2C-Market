package com.marketplace.backend.service.search;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Product embeddings (table product_embeddings, created here rather than by JPA because of the vector type).
 *  - PostgreSQL with the pgvector extension (Neon, Supabase, the pgvector/pgvector Docker image...): column vector(N),
 *    HNSW index, nearest neighbours found by the database with the cosine distance operator {@code <=>};
 *  - without pgvector: column real[], the vectors kept in memory and compared in Java
 *    (fine for a few tens of thousands of products).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStore {

    public static final int DIMS = 256;

    private final JdbcTemplate jdbc;
    private volatile boolean pgvector;
    // fallback mode: product id -> unit vector
    private final Map<Long, float[]> memory = new ConcurrentHashMap<>();
    private volatile boolean memoryLoaded;

    public record Hit(long productId, double score) {
    }

    @PostConstruct
    void init() {
        // ask first: a failing CREATE EXTENSION would make the pool throw away its connection and log a stack trace
        Boolean installable = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector')", Boolean.class);
        pgvector = false;
        if (Boolean.TRUE.equals(installable)) {
            try {
                jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
                pgvector = true;
            } catch (Exception e) {
                log.info("pgvector is installed but could not be enabled ({}): using real[]", e.getMessage());
            }
        }
        String type = jdbc.query("SELECT udt_name FROM information_schema.columns WHERE table_name = 'product_embeddings' AND column_name = 'embedding'",
                rs -> rs.next() ? rs.getString(1) : null);
        if (type != null && type.equals("vector") != pgvector) {
            // the database changed (e.g. pgvector installed later): start over, the scheduler re-embeds
            jdbc.execute("DROP TABLE product_embeddings");
            type = null;
        }
        if (type == null) {
            jdbc.execute("CREATE TABLE product_embeddings (product_id BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE, "
                    + "model VARCHAR(60) NOT NULL, content_hash VARCHAR(32) NOT NULL, embedding "
                    + (pgvector ? "vector(" + DIMS + ")" : "real[]") + " NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT now())");
        }
        if (pgvector) {
            try {
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_product_embeddings_hnsw ON product_embeddings USING hnsw (embedding vector_cosine_ops)");
            } catch (Exception e) {
                log.info("pgvector without HNSW (older version): exact search");
            }
        }
        log.info("Product embeddings: {}", pgvector ? "pgvector" : "real[] + in-memory cosine (pgvector not installed)");
    }

    public boolean pgvector() {
        return pgvector;
    }

    /** Active products whose text changed since they were embedded (or never were): id, name, category, description, hash. */
    public List<Object[]> stale(int limit) {
        return jdbc.query("SELECT p.id, p.name, c.name, p.description, x.h FROM products p "
                        + "LEFT JOIN categories c ON c.id = p.category_id "
                        + "LEFT JOIN product_embeddings e ON e.product_id = p.id "
                        + "CROSS JOIN LATERAL (SELECT md5(coalesce(p.name, '') || '|' || coalesce(c.name, '') || '|' "
                        + "|| left(coalesce(p.description, ''), 800)) AS h) x "
                        + "WHERE p.active AND e.content_hash IS DISTINCT FROM x.h ORDER BY p.sold_count DESC, p.id LIMIT ?",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)}, limit);
    }

    public void save(long productId, String model, String hash, float[] v) {
        float[] u = unit(v);
        String upsert = "INSERT INTO product_embeddings (product_id, model, content_hash, embedding, updated_at) VALUES (?, ?, ?, "
                + (pgvector ? "?::vector" : "?") + ", now()) ON CONFLICT (product_id) DO UPDATE SET model = EXCLUDED.model, "
                + "content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding, updated_at = now()";
        jdbc.update(con -> {
            var ps = con.prepareStatement(upsert);
            ps.setLong(1, productId);
            ps.setString(2, model);
            ps.setString(3, hash);
            if (pgvector) {
                ps.setString(4, literal(u));
            } else {
                Float[] boxed = new Float[u.length];
                for (int i = 0; i < u.length; i++) boxed[i] = u[i];
                ps.setArray(4, con.createArrayOf("float4", boxed));
            }
            return ps;
        });
        if (!pgvector) memory.put(productId, u);
    }

    /** The products closest in meaning to the query vector, best first (live products of active shops only). */
    public List<Hit> nearest(float[] query, int limit, double minScore) {
        float[] q = unit(query);
        if (pgvector) {
            String lit = literal(q);
            return jdbc.query("SELECT e.product_id, 1 - (e.embedding <=> ?::vector) AS score FROM product_embeddings e "
                            + "JOIN products p ON p.id = e.product_id JOIN shops s ON s.id = p.shop_id "
                            + "WHERE p.active AND s.active ORDER BY e.embedding <=> ?::vector LIMIT ?",
                    (rs, i) -> new Hit(rs.getLong(1), rs.getDouble(2)), lit, lit, limit)
                    .stream().filter(h -> h.score() >= minScore).toList();
        }
        loadMemory();
        Set<Long> live = new HashSet<>(jdbc.queryForList(
                "SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE p.active AND s.active", Long.class));
        PriorityQueue<Hit> best = new PriorityQueue<>(Comparator.comparingDouble(Hit::score));
        memory.forEach((id, v) -> {
            if (!live.contains(id) || v.length != q.length) return;
            double s = 0;
            for (int i = 0; i < q.length; i++) s += q[i] * v[i];
            if (s < minScore) return;
            best.add(new Hit(id, s));
            if (best.size() > limit) best.poll();
        });
        List<Hit> out = new ArrayList<>(best);
        out.sort(Comparator.comparingDouble(Hit::score).reversed());
        return out;
    }

    public Map<String, Object> counts() {
        Long done = jdbc.queryForObject("SELECT count(*) FROM product_embeddings e JOIN products p ON p.id = e.product_id WHERE p.active", Long.class);
        Long total = jdbc.queryForObject("SELECT count(*) FROM products WHERE active", Long.class);
        return Map.of("indexed", done == null ? 0 : done, "activeProducts", total == null ? 0 : total);
    }

    /** Re-embed everything (e.g. after changing the model): the scheduler picks the products up again. */
    public void clear() {
        jdbc.update("DELETE FROM product_embeddings");
        memory.clear();
    }

    private void loadMemory() {
        if (memoryLoaded) return;
        synchronized (this) {
            if (memoryLoaded) return;
            jdbc.query("SELECT product_id, embedding FROM product_embeddings", rs -> {
                Array a = rs.getArray(2);
                Float[] f = (Float[]) a.getArray();
                float[] v = new float[f.length];
                for (int i = 0; i < f.length; i++) v[i] = f[i];
                memory.put(rs.getLong(1), v);
            });
            memoryLoaded = true;
        }
    }

    static float[] unit(float[] v) {
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n);
        if (n == 0) return v;
        float[] u = new float[v.length];
        for (int i = 0; i < v.length; i++) u[i] = (float) (v[i] / n);
        return u;
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
