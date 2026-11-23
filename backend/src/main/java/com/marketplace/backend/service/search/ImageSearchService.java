package com.marketplace.backend.service.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductImageIndex;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductImageIndexRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.service.AIService;
import com.marketplace.backend.service.UploadService;
import com.marketplace.backend.service.VisionCategoryService;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Tim kiem bang hinh anh". Two layers, like the other AI features:
 *  1. look-alikes. With the AI service running: MobileNetV2 (review-ai-service/app/vision.py) turns every cover image
 *     and the uploaded picture into 1280 numbers; the closest vectors (cosine) are the most alike products, and products
 *     of the categories the trained vision model sees in the picture get a small bonus. Tested on 256 product photos
 *     against the other covers: the right product first 79% of the time, in the top 5 93% (colour matching: 38% / 60%).
 *     MobileNet also reads WEBP, which Java's ImageIO (and so the colour index) cannot.
 *     Without the service: colour and shape (ImageFeatures), as before;
 *  2. with a vision model configured (OpenAI / Claude) and a logged-in user: the model names the product in the picture
 *     ("ao khoac gio nam mau xanh"), and products matching that description (semantic search, else keywords) are
 *     ranked first, ordered by how alike the pictures are.
 * Cover images are indexed in the background (and on the admin's request) whenever a product's image changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final double MIN_VISUAL = 0.45;     // below this, two pictures are not alike (colour and shape)
    // MobileNet cosine: ~0.51 for two random products, ~0.60 at the 90th percentile, ~0.86 for two photos of one product.
    // Shown to the buyer as (cosine - 0.5) / 0.45, so "Giống 80%" means a near certain match.
    static final double DEEP_ZERO = 0.5, DEEP_RANGE = 0.45, MIN_DEEP = 0.27;
    static final double CATEGORY_BONUS = 0.2;   // x the probability the photo is of that product's category
    private static final Map<String, String> TYPES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "gif", "image/gif");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROMPT = """
            Bạn là trợ lý tìm kiếm của sàn thương mại điện tử Shoppi. Nhìn ảnh và cho biết sản phẩm chính trong ảnh.
            Chỉ trả về JSON: {"description": "tên sản phẩm ngắn gọn bằng tiếng Việt, kèm màu sắc/kiểu dáng nếu rõ",
            "keywords": ["2 đến 5 từ khóa tìm kiếm tiếng Việt, từ quan trọng nhất"]}. Ảnh không có sản phẩm thì description rỗng.""";

    private final ProductImageIndexRepository indexRepository;
    private final ProductRepository productRepository;
    private final SemanticSearchService semanticSearch;
    private final AIService aiService;
    private final UploadService uploadService;
    private final WebClient webClient;
    private final VisionCategoryService vision;

    // productId -> [vector], [categoryId]; dropped whenever the index changes
    private volatile Map<Long, Object[]> vectors;

    public record Upload(byte[] bytes, String mime) {
    }

    // ==================== search ====================

    @Transactional(readOnly = true)
    public Map<String, Object> search(MultipartFile file, boolean useAi, int limit) {
        Upload up = read(file);
        BufferedImage img = decode(up.bytes());
        ImageFeatures query = img == null ? null : ImageFeatures.of(img);
        VisionCategoryService.Seen seen = vision.see(up.bytes(), 10);   // null when the AI service is off

        // 2. what the picture shows, in words (vision model)
        String description = null;
        List<String> keywords = List.of();
        if (useAi) {
            String answer = aiService.describeImageOrNull(PROMPT, "Sản phẩm trong ảnh là gì?", up.bytes(), up.mime());
            if (answer != null) {
                try {
                    JsonNode n = JSON.readTree(answer.replaceAll("(?s)^.*?(\\{.*}).*$", "$1"));
                    description = n.path("description").asText("").trim();
                    List<String> k = new ArrayList<>();
                    n.path("keywords").forEach(x -> { if (!x.asText().isBlank()) k.add(x.asText().trim()); });
                    keywords = k.stream().limit(5).toList();
                } catch (Exception e) {
                    log.debug("Unreadable image description: {}", answer);
                }
            }
        }
        if (query == null && seen == null && (description == null || description.isBlank())) {
            throw new ApiException("Không đọc được ảnh này. Vui lòng dùng ảnh JPG hoặc PNG", HttpStatus.BAD_REQUEST);
        }

        // 1. look-alikes: MobileNet when the AI service runs, else colour and shape
        Map<Long, Double> visual = new HashMap<>();
        boolean deep = false;
        Map<Long, Object[]> index = seen == null ? Map.of() : vectors();
        if (!index.isEmpty()) {
            deep = true;
            index.forEach((id, v) -> {
                double s = VisionCategoryService.cosine(seen.vector(), (float[]) v[0])
                        + CATEGORY_BONUS * seen.categories().getOrDefault((Long) v[1], 0.0);
                visual.put(id, Math.max(0, Math.min(1, (s - DEEP_ZERO) / DEEP_RANGE)));
            });
        } else if (query != null) {
            for (ProductImageIndex i : indexRepository.findComparable()) {
                visual.put(i.getProductId(), query.similarity(ImageFeatures.parse(i.getHistogram(), i.getDhash())));
            }
        }

        // products named by the description: semantic search first, then keyword matches
        Map<Long, Double> text = new LinkedHashMap<>();
        if (description != null && !description.isBlank()) {
            List<Product> sem = semanticSearch.nearestProducts(description, 40);
            for (int i = 0; i < sem.size(); i++) text.putIfAbsent(sem.get(i).getId(), 1.0 - i * 0.01);
            for (String k : keywords.isEmpty() ? List.of(description) : keywords) {
                String n = TextNormalizer.normalize(k);
                if (n == null || n.length() < 2) continue;
                for (Product p : productRepository.findForChatbot("%" + n + "%", PageRequest.of(0, 40))) text.merge(p.getId(), 0.8, Math::max);
            }
        }

        double min = deep ? MIN_DEEP : MIN_VISUAL;
        Map<Long, Double> score = new HashMap<>();
        if (text.isEmpty()) {
            visual.forEach((id, v) -> { if (v >= min) score.put(id, v); });
        } else {
            // named products first; among them, and after them, the more alike the picture the higher
            text.forEach((id, t) -> score.put(id, 0.55 * t + 0.45 * visual.getOrDefault(id, 0.0)));
            visual.forEach((id, v) -> { if (v >= min + 0.1) score.merge(id, 0.45 * v, Math::max); });
        }
        List<Long> ids = score.entrySet().stream().sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(Math.min(Math.max(limit, 1), 60) * 2L).map(Map.Entry::getKey).toList();
        Map<Long, Product> products = productRepository.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long id : ids) {
            Product p = products.get(id);
            if (p == null || !p.isActive() || !p.getShop().isActive()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("product", ProductResponse.from(p));
            m.put("similarity", (int) Math.round(visual.getOrDefault(id, 0.0) * 100));
            m.put("matchedText", text.containsKey(id));
            items.add(m);
            if (items.size() >= limit) break;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("description", description == null || description.isBlank() ? null : description);
        out.put("keywords", keywords);
        String look = deep ? "MOBILENET" : "VISUAL";
        out.put("engine", text.isEmpty() ? look : "AI+" + look);
        out.put("indexed", visual.size());
        out.put("items", items);
        return out;
    }

    private Upload read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("Vui lòng chọn ảnh", HttpStatus.BAD_REQUEST);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String mime = TYPES.get(ext);
        if (mime == null) throw new ApiException("Chỉ hỗ trợ ảnh JPG, PNG, WEBP, GIF", HttpStatus.BAD_REQUEST);
        if (file.getSize() > MAX_BYTES) throw new ApiException("Ảnh tối đa 5MB", HttpStatus.BAD_REQUEST);
        try {
            return new Upload(file.getBytes(), mime);
        } catch (IOException e) {
            throw new ApiException("Không đọc được ảnh", HttpStatus.BAD_REQUEST);
        }
    }

    static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));   // null for formats ImageIO can't read (e.g. WEBP)
        } catch (Exception e) {
            return null;
        }
    }

    /** The MobileNet vectors of every cover image, with the product's category, read once until the index changes. */
    private Map<Long, Object[]> vectors() {
        Map<Long, Object[]> v = vectors;
        if (v == null) {
            List<ProductImageIndex> rows = indexRepository.findEmbedded();
            Map<Long, Long> cats = new HashMap<>();
            for (int k = 0; k < rows.size(); k += 1000) {
                List<Long> ids = rows.subList(k, Math.min(rows.size(), k + 1000)).stream().map(ProductImageIndex::getProductId).toList();
                for (Object[] r : indexRepository.categoriesOf(ids)) cats.put((Long) r[0], (Long) r[1]);
            }
            v = new HashMap<>();
            for (ProductImageIndex i : rows) {
                float[] f = VisionCategoryService.decode(i.getEmbedding());
                if (f != null) v.put(i.getProductId(), new Object[]{f, cats.get(i.getProductId())});
            }
            vectors = v;
        }
        return v;
    }

    // ==================== index ====================

    /** Indexes up to `max` products whose cover image is new or changed. Returns how many were done. */
    @Transactional
    public int indexPending(int max) {
        int done = 0;
        for (Product p : indexRepository.needingIndex(PageRequest.of(0, max))) {
            ImageFeatures f = null;
            String embedding = null;
            try {
                byte[] bytes = load(p.getThumbnailUrl());
                BufferedImage img = bytes == null ? null : decode(bytes);
                if (img != null) f = ImageFeatures.of(img);
                var seen = bytes == null ? null : vision.see(bytes, 0);
                if (seen != null) embedding = VisionCategoryService.encode(seen.vector());
            } catch (Exception e) {
                log.debug("Image of product {} not indexed: {}", p.getId(), e.getMessage());
            }
            indexRepository.save(ProductImageIndex.builder().productId(p.getId()).imageUrl(p.getThumbnailUrl())
                    .histogram(f == null ? null : f.histogramText()).dhash(f == null ? null : f.dhash())
                    .embedding(embedding).indexedAt(LocalDateTime.now()).build());
            done++;
        }
        if (done > 0) vectors = null;
        return done;
    }

    /**
     * MobileNet vectors for indexed images that have none (the AI service was off when they were indexed).
     * Stops at the first failure while nothing succeeded (the service is down: try again later); after a success,
     * an image the service cannot read is marked "" so it is not retried.
     */
    @Transactional
    public int embedPending(int max) {
        if (!vision.configured()) return 0;
        int done = 0;
        for (ProductImageIndex i : indexRepository.needingEmbedding(PageRequest.of(0, max))) {
            byte[] bytes = null;
            try {
                bytes = load(i.getImageUrl());
            } catch (Exception e) {
                log.debug("Image {} not readable: {}", i.getImageUrl(), e.getMessage());
            }
            var seen = bytes == null ? null : vision.see(bytes, 0);
            if (seen == null && bytes != null && done == 0) break;
            i.setEmbedding(seen == null ? "" : VisionCategoryService.encode(seen.vector()));
            indexRepository.save(i);
            done++;
        }
        if (done > 0) vectors = null;
        return done;
    }

    // our own uploads from disk; other http(s) images fetched with a short timeout
    private byte[] load(String url) throws IOException {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("/uploads/")) {
            Path root = uploadService.root();
            Path f = root.resolve(url.substring("/uploads/".length())).normalize();
            if (!f.startsWith(root) || !Files.isRegularFile(f) || Files.size(f) > MAX_BYTES) return null;
            return Files.readAllBytes(f);
        }
        if (url.startsWith("https://") || url.startsWith("http://")) {
            return webClient.get().uri(url).retrieve().bodyToMono(byte[].class).block(Duration.ofSeconds(8));
        }
        return null;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void background() {
        try {
            indexPending(100);
            embedPending(100);
        } catch (Exception e) {
            log.warn("Image index run failed: {}", e.getMessage());
        }
    }

    // ==================== admin ====================

    public Map<String, Object> status() {
        return Map.of("indexed", indexRepository.countByHistogramIsNotNull(), "products", indexRepository.countProductsWithImage(),
                "pending", indexRepository.needingIndex(PageRequest.of(0, 500)).size(), "aiReady", aiService.isConfigured(),
                "embedded", indexRepository.countEmbedded(), "visionReady", vision.configured());
    }

    /** Indexes everything pending now (batches of 200). */
    public Map<String, Object> reindexAll() {
        int total = 0, n;
        do {
            n = indexPending(200);
            total += n;
        } while (n == 200 && total < 20_000);
        int e, embedded = 0;
        do {
            e = embedPending(200);
            embedded += e;
        } while (e == 200 && embedded < 20_000);
        Map<String, Object> m = new LinkedHashMap<>(status());
        m.put("done", total);
        m.put("embeddedNow", embedded);
        return m;
    }
}
