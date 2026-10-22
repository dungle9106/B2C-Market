package com.marketplace.backend.service.reviewhub;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.entity.ReviewHubItem.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ReviewHubItemRepository;
import com.marketplace.backend.repository.ReviewRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AdminAuditService;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.ReviewService;
import com.marketplace.backend.util.TextNormalizer;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Trung tâm đánh giá" (review hub) for sellers: every review of the shop - Shoppi's own and those imported from
 * other marketplaces - in one place, analysed (sentiment, issues), ranked by how urgently it needs an answer,
 * checked for signs of fake reviews, with drafted replies to approve and the recurring problems to fix at the root.
 */
@Slf4j
@Service
@Transactional
public class ReviewHubService {

    static final int ANOMALY_WINDOW_DAYS = 120;
    static final int AI_BATCH = 15;

    private final ReviewHubItemRepository repository;
    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ReviewService reviewService;
    private final ReviewAiService ai;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final TransactionTemplate tx;

    public ReviewHubService(ReviewHubItemRepository repository, ReviewRepository reviewRepository, ShopRepository shopRepository,
                            ProductRepository productRepository, ReviewService reviewService, ReviewAiService ai,
                            NotificationService notificationService, AdminAuditService auditService, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.reviewRepository = reviewRepository;
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.reviewService = reviewService;
        this.ai = ai;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(txManager);
    }

    // ==================== Shoppi reviews ====================

    /** Brings in the shop's Shoppi reviews not in the hub yet (first visit, or anything missed). */
    public Map<String, Object> sync() {
        Shop shop = myShop();
        int added = syncShop(shop);
        if (added > 0) refreshShop(shop.getId());
        return Map.of("added", added);
    }

    int syncShop(Shop shop) {
        List<Long> known = repository.syncedReviewIds(shop.getId());
        List<Review> fresh = reviewRepository.notSynced(shop.getId(), known.isEmpty() ? List.of(-1L) : known);
        for (Review r : fresh) repository.save(fromReview(r));
        return fresh.size();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewWritten(ReviewHubEvents.ReviewWritten e) {
        try {
            reviewRepository.findById(e.reviewId()).ifPresent(r -> {
                if (repository.findByReviewId(r.getId()).isPresent()) return;
                ReviewHubItem i = repository.save(fromReview(r));
                refreshShop(i.getShopId());
                alertIfUrgent(i, r.getProduct().getShop());
            });
        } catch (Exception ex) {
            log.warn("Review hub: review {} not added: {}", e.reviewId(), ex.getMessage());
        }
    }

    // a reply written from the old reviews page (or from here) closes the item
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewReplied(ReviewHubEvents.ReviewReplied e) {
        repository.findByReviewId(e.reviewId()).ifPresent(i -> {
            if (i.getStatus() != Status.REPLIED || !Objects.equals(i.getReplyText(), e.reply())) {
                i.setStatus(Status.REPLIED);
                i.setReplyText(e.reply());
                i.setReplyChannel("SHOPPI");
                if (i.getRepliedAt() == null) i.setRepliedAt(LocalDateTime.now());
                ReviewPriority.apply(i, List.of(), LocalDateTime.now());
            }
        });
    }

    private ReviewHubItem fromReview(Review r) {
        ReviewHubItem i = ReviewHubItem.builder()
                .shopId(r.getProduct().getShop().getId()).source(Source.SHOPPI).externalId("R" + r.getId()).reviewId(r.getId())
                .productId(r.getProduct().getId()).productName(r.getProduct().getName())
                .reviewerName(r.getBuyer() != null ? r.getBuyer().getFullName() : null)
                .reviewerKey(r.getBuyer() != null ? "u" + r.getBuyer().getId() : null)
                .orderRef(r.getOrderItem() != null && r.getOrderItem().getOrder() != null ? r.getOrderItem().getOrder().getCode() : null)
                .rating(r.getRating()).content(ReviewTextAnalyzer.cut(r.getComment(), 2000))
                .mediaCount((r.getImageUrls() == null ? 0 : r.getImageUrls().size()) + (r.getVideoUrl() != null ? 1 : 0))
                .reviewedAt(r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now()).importedAt(LocalDateTime.now())
                .status(r.getSellerReply() != null ? Status.REPLIED : Status.NEW)
                .replyText(r.getSellerReply()).replyChannel(r.getSellerReply() != null ? "SHOPPI" : null)
                .anomalyStatus(AnomalyStatus.NONE)
                .build();
        analyzeRules(i);
        return i;
    }

    // ==================== other marketplaces ====================

    /** Imports a review export (CSV / Excel) of another marketplace. */
    public Map<String, Object> importFile(String filename, byte[] bytes, String sourceName) {
        Shop shop = myShop();
        Source source = source(sourceName);
        if (source == Source.SHOPPI) throw bad("Đánh giá trên Shoppi được đồng bộ tự động, không cần nhập file");
        List<ReviewFileParser.Row> rows;
        try {
            rows = ReviewFileParser.parse(filename, bytes);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
        return ingestRows(shop, source, rows, filename);
    }

    /**
     * The same as a file, as JSON (for a connector or a script pulling reviews from a marketplace API):
     * [{ externalId?, rating, content, product, reviewer?, time?, order?, media? }]
     */
    public Map<String, Object> ingest(String sourceName, List<Map<String, Object>> items) {
        Shop shop = myShop();
        Source source = source(sourceName);
        if (source == Source.SHOPPI) throw bad("Đánh giá trên Shoppi được đồng bộ tự động");
        if (items == null || items.isEmpty()) throw bad("Không có đánh giá nào");
        if (items.size() > ReviewFileParser.MAX_ROWS) throw bad("Mỗi lần tối đa " + ReviewFileParser.MAX_ROWS + " đánh giá");
        List<ReviewFileParser.Row> rows = new ArrayList<>();
        for (int k = 0; k < items.size(); k++) {
            Map<String, String> v = new HashMap<>();
            items.get(k).forEach((key, val) -> v.put(key, val == null ? "" : String.valueOf(val)));
            rows.add(new ReviewFileParser.Row(k + 1, v));
        }
        return ingestRows(shop, source, rows, "api");
    }

    private Map<String, Object> ingestRows(Shop shop, Source source, List<ReviewFileParser.Row> rows, String origin) {
        String batch = source.name() + "-" + System.currentTimeMillis();
        List<Product> products = productRepository.findByShopId(shop.getId());
        Map<String, Product> productByName = new HashMap<>();
        products.forEach(p -> productByName.put(TextNormalizer.normalize(p.getName()), p));
        int added = 0, duplicates = 0, matched = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ReviewFileParser.Row row : rows) {
            Map<String, String> v = row.values();
            Integer rating = ReviewFileParser.rating(v.get("rating"));
            if (rating == null) {
                if (skipped.size() < 50) skipped.add(Map.of("line", row.line(), "reason", "Số sao không hợp lệ: \"" + ReviewTextAnalyzer.cut(v.getOrDefault("rating", ""), 20) + "\""));
                continue;
            }
            String content = blankToNull(v.get("content"));
            String reviewer = blankToNull(v.get("reviewer"));
            LocalDateTime time = ReviewFileParser.time(v.get("time"));
            if (time == null) time = LocalDateTime.now();
            if (time.isAfter(LocalDateTime.now().plusDays(1))) time = LocalDateTime.now();
            String ext = blankToNull(v.get("externalId"));
            if (ext == null) ext = "H" + sha(source + "|" + reviewer + "|" + time.toLocalDate() + "|" + rating + "|" + content + "|" + v.get("product")).substring(0, 32);
            ext = ReviewTextAnalyzer.cut(ext, 120);
            if (!seen.add(ext) || repository.findByShopIdAndSourceAndExternalId(shop.getId(), source, ext).isPresent()) {
                duplicates++;
                continue;
            }
            String productName = blankToNull(v.get("product"));
            Product product = match(productByName, productName);
            if (product != null) matched++;
            String reply = blankToNull(v.get("reply"));
            ReviewHubItem i = ReviewHubItem.builder()
                    .shopId(shop.getId()).source(source).externalId(ext)
                    .productId(product != null ? product.getId() : null)
                    .productName(ReviewTextAnalyzer.cut(productName != null ? productName : product != null ? product.getName() : null, 300))
                    .reviewerName(ReviewTextAnalyzer.cut(reviewer, 120))
                    .reviewerKey(reviewer == null ? null : source.name().charAt(0) + sha(TextNormalizer.normalize(reviewer)).substring(0, 20))
                    .orderRef(ReviewTextAnalyzer.cut(blankToNull(v.get("order")), 60))
                    .rating(rating).content(ReviewTextAnalyzer.cut(content, 2000)).mediaCount(ReviewFileParser.media(v.get("media")))
                    .reviewedAt(time).importedAt(LocalDateTime.now()).importBatch(batch)
                    .status(reply != null ? Status.REPLIED : Status.NEW).replyText(ReviewTextAnalyzer.cut(reply, 1000))
                    .replyChannel(reply != null ? "EXTERNAL" : null).anomalyStatus(AnomalyStatus.NONE)
                    .build();
            analyzeRules(i);
            repository.save(i);
            added++;
        }
        if (added > 0) {
            refreshShop(shop.getId());
            checkRecurring(shop);
        }
        auditless(shop, source, added, origin);
        return AdminMap.of("source", source.name(), "batch", batch, "rows", rows.size(), "added", added, "duplicates", duplicates,
                "matchedProducts", matched, "skipped", skipped);
    }

    // an imported product name -> one of the shop's products (same name, or one name containing the other)
    private static Product match(Map<String, Product> byName, String name) {
        if (name == null) return null;
        String n = TextNormalizer.normalize(name);
        Product p = byName.get(n);
        if (p != null) return p;
        if (n.length() < 8) return null;
        return byName.entrySet().stream().filter(e -> e.getKey().length() >= 8 && (e.getKey().contains(n) || n.contains(e.getKey())))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private void auditless(Shop shop, Source source, int added, String origin) {
        log.info("Review hub: shop {} imported {} reviews from {} ({})", shop.getId(), added, source, origin);
    }

    /** A template file to fill in by hand or to compare with a marketplace export. */
    @Transactional(readOnly = true)
    public byte[] template() {
        String csv = "﻿Mã đánh giá,Tên sản phẩm,Người mua,Số sao,Nội dung đánh giá,Thời gian,Mã đơn hàng,Số ảnh,Phản hồi của shop\n"
                + "SP-001,Áo thun nam cotton,nguyenvana,5,\"Vải mát, form đẹp, giao nhanh\",15/10/2026 09:30,240915ABC,2,\n"
                + "SP-002,Áo thun nam cotton,tranthib,1,\"Áo bị bung chỉ sau 1 lần giặt, không giống hình\",16/10/2026 20:10,240916XYZ,1,\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    // ==================== analysis ====================

    /** Rules (always), then the FastAPI service when configured; the LLM pass runs later in the background. */
    void analyzeRules(ReviewHubItem i) {
        ReviewTextAnalyzer.Result r = ReviewTextAnalyzer.analyze(i.getRating(), i.getContent());
        i.setSentiment(Sentiment.valueOf(r.sentiment()));
        i.setSentimentScore(r.score());
        if (!i.isIssuesManual()) {
            i.setIssues(r.issues().isEmpty() ? null : String.join(",", r.issues()));
            i.setIssueSummary(r.summary());
        }
        i.setAnalyzedBy("RULE");
        i.setAnalyzedAt(LocalDateTime.now());
        i.setAiPending(ai.llmAvailable() && i.getContent() != null && !i.getContent().isBlank() && !i.isIssuesManual());
        ReviewPriority.apply(i, r.severeHits(), LocalDateTime.now());
    }

    // the FastAPI service's reading of the new items, then priorities and anomaly signals for the whole shop
    void refreshShop(Long shopId) {
        List<ReviewHubItem> recent = repository.since(shopId, LocalDateTime.now().minusDays(ANOMALY_WINDOW_DAYS));
        if (ai.serviceAvailable()) {
            List<ReviewHubItem> fresh = recent.stream().filter(i -> "RULE".equals(i.getAnalyzedBy()) && i.getContent() != null && !i.isIssuesManual()).toList();
            for (int k = 0; k < fresh.size(); k += 200) {
                List<ReviewHubItem> part = fresh.subList(k, Math.min(fresh.size(), k + 200));
                Map<Long, ReviewAiService.Classified> res = ai.classifyByService(part);
                for (ReviewHubItem i : part) {
                    ReviewAiService.Classified c = res.get(i.getId());
                    if (c == null) continue;
                    applyClassification(i, c, "SERVICE");
                }
            }
        }
        ReviewAnomalyDetector.run(recent);
        repository.saveAll(recent);
    }

    private void applyClassification(ReviewHubItem i, ReviewAiService.Classified c, String by) {
        if (c.sentiment() != null) i.setSentiment(Sentiment.valueOf(c.sentiment()));
        if (c.score() != null) i.setSentimentScore(c.score());
        if (!i.isIssuesManual()) {
            List<String> issues = new ArrayList<>(c.issues());
            if (issues.isEmpty() && i.getRating() <= 2) issues.add("OTHER");
            i.setIssues(issues.isEmpty() ? null : String.join(",", issues));
            if (c.summary() != null) i.setIssueSummary(c.summary());
        }
        if (c.suspicious()) {
            LinkedHashSet<String> flags = new LinkedHashSet<>(i.getAnomalyFlags() == null ? List.of() : List.of(i.getAnomalyFlags().split(",")));
            flags.add("AI_SUSPECT");
            i.setAnomalyFlags(String.join(",", flags));
            String note = "AI: " + Objects.requireNonNullElse(c.suspiciousReason(), "nội dung đáng ngờ");
            i.setAnomalyNote(i.getAnomalyNote() == null ? note : ReviewTextAnalyzer.cut(i.getAnomalyNote() + " · " + note, 600));
        }
        i.setAnalyzedBy(by);
        i.setAnalyzedAt(LocalDateTime.now());
        ReviewPriority.apply(i, ReviewTextAnalyzer.analyze(i.getRating(), i.getContent()).severeHits(), LocalDateTime.now());
    }

    /** The LLM pass: reviews waiting for it, a batch at a time (outside any transaction while the model thinks). */
    @Scheduled(fixedDelay = 20_000, initialDelay = 40_000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aiPass() {
        if (!ai.llmAvailable()) return;
        List<ReviewHubItem> batch = tx.execute(s -> repository.aiPending(PageRequest.of(0, AI_BATCH)));
        if (batch == null || batch.isEmpty()) return;
        Map<Long, ReviewAiService.Classified> res = ai.classify(batch);
        tx.executeWithoutResult(s -> {
            Set<Long> shops = new HashSet<>();
            for (ReviewHubItem stale : batch) {
                ReviewHubItem i = repository.findById(stale.getId()).orElse(null);
                if (i == null) continue;
                ReviewAiService.Classified c = res.get(i.getId());
                if (c != null) applyClassification(i, c, "AI");
                i.setAiPending(false);   // tried once; the rules' reading stays if the model did not answer
                shops.add(i.getShopId());
            }
            for (Long shopId : shops) {
                List<ReviewHubItem> recent = repository.since(shopId, LocalDateTime.now().minusDays(ANOMALY_WINDOW_DAYS));
                ReviewAnomalyDetector.run(recent);
                repository.saveAll(recent);
                shopRepository.findById(shopId).ifPresent(this::checkRecurring);
            }
        });
    }

    /** Re-reads every review of the shop (after the rules or the AI configuration changed). */
    public Map<String, Object> reanalyze() {
        Shop shop = myShop();
        syncShop(shop);
        List<ReviewHubItem> all = repository.since(shop.getId(), LocalDateTime.of(2000, 1, 1, 0, 0));
        all.forEach(this::analyzeRules);
        repository.saveAll(all);
        refreshShop(shop.getId());
        return Map.of("analyzed", all.size(), "aiQueued", all.stream().filter(ReviewHubItem::isAiPending).count());
    }

    // ==================== alerts ====================

    private void alertIfUrgent(ReviewHubItem i, Shop shop) {
        if (i.getPriorityLevel() != Level.URGENT) return;
        notificationService.notify(shop.getSeller().getId(), NotificationType.SYSTEM, "Đánh giá cần trả lời gấp (" + i.getRating() + " sao)",
                ReviewTextAnalyzer.cut(Objects.requireNonNullElse(i.getIssueSummary(), Objects.requireNonNullElse(i.getContent(), "")), 160)
                        + " · Hạn trả lời: " + i.getReplyDueAt().toLocalTime().withNano(0) + " " + i.getReplyDueAt().toLocalDate(),
                "/seller/review-hub?item=" + i.getId());
    }

    /** A complaint repeating on one product (3+ in 14 days, more than the 14 days before): tell the seller once. */
    void checkRecurring(Shop shop) {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewHubItem> items = repository.since(shop.getId(), now.minusDays(28));
        Map<String, long[]> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (ReviewHubItem i : items) {
            if (i.getIssues() == null || i.getSentiment() == Sentiment.POSITIVE) continue;
            boolean recent = i.getReviewedAt().isAfter(now.minusDays(14));
            for (String issue : i.getIssues().split(",")) {
                if (issue.equals("OTHER")) continue;
                String key = ReviewAnomalyDetector.productKey(i) + "|" + issue;
                counts.computeIfAbsent(key, k -> new long[2])[recent ? 0 : 1]++;
                names.putIfAbsent(key, Objects.requireNonNullElse(i.getProductName(), "Sản phẩm"));
            }
        }
        counts.forEach((key, c) -> {
            if (c[0] >= 3 && c[0] > c[1]) {
                String issue = key.substring(key.lastIndexOf('|') + 1);
                // one unread notification per product and issue (later ones update it)
                notificationService.notifyCollapsed(shop.getSeller().getId(), NotificationType.SYSTEM,
                        "Vấn đề lặp lại: " + ReviewTextAnalyzer.label(issue),
                        c[0] + " đánh giá trong 14 ngày về \"" + ReviewTextAnalyzer.label(issue).toLowerCase(Locale.ROOT) + "\" của "
                                + ReviewTextAnalyzer.cut(names.get(key), 60) + ". Kiểm tra lô hàng và xử lý từ gốc.",
                        "/seller/review-hub?tab=issues&key=" + key.substring(0, key.lastIndexOf('|')) + "&issue=" + issue);
            }
        });
    }

    // ==================== inbox ====================

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(Map<String, String> f, int page, int size) {
        Shop shop = myShop();
        Specification<ReviewHubItem> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("shopId"), shop.getId()));
            if (has(f, "source")) p.add(cb.equal(root.get("source"), source(f.get("source"))));
            if (has(f, "rating")) p.add(cb.equal(root.get("rating"), Integer.parseInt(f.get("rating"))));
            if (has(f, "maxRating")) p.add(cb.le(root.get("rating"), Integer.parseInt(f.get("maxRating"))));
            if (has(f, "sentiment")) p.add(cb.equal(root.get("sentiment"), Sentiment.valueOf(f.get("sentiment"))));
            if (has(f, "level")) p.add(cb.equal(root.get("priorityLevel"), Level.valueOf(f.get("level"))));
            if (has(f, "status")) {
                if (f.get("status").equals("OPEN")) p.add(root.get("status").in(Status.NEW, Status.DRAFTED));
                else p.add(cb.equal(root.get("status"), Status.valueOf(f.get("status"))));
            }
            if (has(f, "issue")) p.add(cb.like(cb.concat(cb.concat(",", root.get("issues")), ","), "%," + f.get("issue") + ",%"));
            if (has(f, "anomaly")) p.add(cb.equal(root.get("anomalyStatus"), AnomalyStatus.valueOf(f.get("anomaly"))));
            if (has(f, "productId")) p.add(cb.equal(root.get("productId"), Long.parseLong(f.get("productId"))));
            if (has(f, "overdue")) p.add(cb.and(root.get("status").in(Status.NEW, Status.DRAFTED), cb.lessThan(root.get("replyDueAt"), LocalDateTime.now()),
                    cb.lessThanOrEqualTo(root.get("rating"), 3)));
            if (has(f, "from")) p.add(cb.greaterThanOrEqualTo(root.get("reviewedAt"), LocalDate.parse(f.get("from")).atStartOfDay()));
            if (has(f, "q")) {
                String like = "%" + f.get("q").trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("content")), like), cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("reviewerName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort sort = switch (f.getOrDefault("sort", "priority")) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "reviewedAt");
            case "oldest" -> Sort.by(Sort.Direction.ASC, "reviewedAt");
            case "rating" -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "reviewedAt"));
            case "anomaly" -> Sort.by(Sort.Direction.DESC, "anomalyScore");
            default -> Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "reviewedAt"));
        };
        try {
            return PageResponse.of(repository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort)), this::view);
        } catch (IllegalArgumentException e) {
            throw bad("Bộ lọc không hợp lệ");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return view(mine(id));
    }

    Map<String, Object> view(ReviewHubItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("source", i.getSource());
        m.put("externalId", i.getExternalId());
        m.put("reviewId", i.getReviewId());
        m.put("productId", i.getProductId());
        m.put("productName", i.getProductName());
        m.put("reviewerName", i.getReviewerName());
        m.put("orderRef", i.getOrderRef());
        m.put("rating", i.getRating());
        m.put("content", i.getContent());
        m.put("mediaCount", i.getMediaCount());
        m.put("reviewedAt", i.getReviewedAt());
        m.put("sentiment", i.getSentiment());
        m.put("sentimentScore", i.getSentimentScore());
        List<String> issues = i.getIssues() == null || i.getIssues().isBlank() ? List.of() : List.of(i.getIssues().split(","));
        m.put("issues", issues.stream().map(k -> Map.of("key", k, "label", ReviewTextAnalyzer.label(k))).toList());
        m.put("issuesManual", i.isIssuesManual());
        m.put("issueSummary", i.getIssueSummary());
        m.put("priority", i.getPriority());
        m.put("priorityLevel", i.getPriorityLevel());
        m.put("priorityReasons", i.getPriorityReasons() == null ? List.of() : List.of(i.getPriorityReasons().split(" \\| ")));
        m.put("replyDueAt", i.getReplyDueAt());
        m.put("overdue", (i.getStatus() == Status.NEW || i.getStatus() == Status.DRAFTED) && i.getRating() <= 3
                && i.getReplyDueAt() != null && i.getReplyDueAt().isBefore(LocalDateTime.now()));
        m.put("anomalyScore", i.getAnomalyScore());
        m.put("anomalyStatus", i.getAnomalyStatus());
        m.put("anomalyFlags", i.getAnomalyFlags() == null ? List.of() : Arrays.stream(i.getAnomalyFlags().split(","))
                .map(k -> Map.of("key", k, "label", ReviewAnomalyDetector.LABELS.getOrDefault(k, k))).toList());
        m.put("anomalyNote", i.getAnomalyNote());
        m.put("analyzedBy", i.getAnalyzedBy());
        m.put("aiPending", i.isAiPending());
        m.put("status", i.getStatus());
        m.put("draftReply", i.getDraftReply());
        m.put("draftSource", i.getDraftSource());
        m.put("replyText", i.getReplyText());
        m.put("repliedAt", i.getRepliedAt());
        m.put("replyChannel", i.getReplyChannel());
        m.put("replyMax", i.getSource() == Source.SHOPPI ? ReviewAiService.SHOPPI_REPLY_MAX : 1000);
        m.put("sellerNote", i.getSellerNote());
        return m;
    }

    // ==================== reply workflow ====================

    /** A drafted reply (AI or template), saved on the item for the seller to edit and approve. */
    public Map<String, Object> draft(Long id) {
        ReviewHubItem i = mine(id);
        if (i.getStatus() == Status.REPLIED) throw bad("Đánh giá này đã được trả lời");
        ReviewAiService.Draft d = ai.draft(i, myShop().getName());
        i.setDraftReply(d.text());
        i.setDraftSource(d.source());
        if (i.getStatus() == Status.NEW) i.setStatus(Status.DRAFTED);
        return view(i);
    }

    /** Drafts for many items at once (the most urgent unanswered first), at most 20. */
    public Map<String, Object> bulkDraft(List<Long> ids) {
        Shop shop = myShop();
        List<ReviewHubItem> items;
        if (ids == null || ids.isEmpty()) {
            items = repository.findAll((root, q, cb) -> cb.and(cb.equal(root.get("shopId"), shop.getId()),
                    cb.equal(root.get("status"), Status.NEW), root.get("priorityLevel").in(Level.URGENT, Level.HIGH)),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "priority"))).getContent();
        } else {
            if (ids.size() > 20) throw bad("Mỗi lần soạn tối đa 20 phản hồi");
            items = repository.findByShopIdAndIdIn(shop.getId(), ids).stream().filter(i -> i.getStatus() != Status.REPLIED).toList();
        }
        int ai = 0;
        for (ReviewHubItem i : items) {
            ReviewAiService.Draft d = this.ai.draft(i, shop.getName());
            i.setDraftReply(d.text());
            i.setDraftSource(d.source());
            if (i.getStatus() == Status.NEW) i.setStatus(Status.DRAFTED);
            if (d.source().equals("AI")) ai++;
        }
        return Map.of("drafted", items.size(), "byAi", ai);
    }

    public Map<String, Object> saveDraft(Long id, String text) {
        ReviewHubItem i = mine(id);
        if (i.getStatus() == Status.REPLIED) throw bad("Đánh giá này đã được trả lời");
        String t = text == null ? "" : text.trim();
        if (t.length() > 1000) throw bad("Bản nháp tối đa 1000 ký tự");
        i.setDraftReply(t.isEmpty() ? null : t);
        i.setDraftSource("SELLER");
        i.setStatus(t.isEmpty() ? Status.NEW : Status.DRAFTED);
        return view(i);
    }

    /**
     * The seller approves a reply: a Shoppi review gets it published right away; for another marketplace the
     * seller posts it there (the page copies it) and the item is marked answered.
     */
    public Map<String, Object> reply(Long id, String text) {
        ReviewHubItem i = mine(id);
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw bad("Vui lòng nhập nội dung phản hồi");
        if (i.getSource() == Source.SHOPPI) {
            if (t.length() > ReviewAiService.SHOPPI_REPLY_MAX) throw bad("Phản hồi trên Shoppi tối đa " + ReviewAiService.SHOPPI_REPLY_MAX + " ký tự");
            reviewService.reply(i.getReviewId(), t);
            i.setReplyChannel("SHOPPI");
        } else {
            if (t.length() > 1000) throw bad("Phản hồi tối đa 1000 ký tự");
            i.setReplyChannel("EXTERNAL");
        }
        i.setReplyText(t);
        i.setRepliedAt(LocalDateTime.now());
        i.setStatus(Status.REPLIED);
        ReviewPriority.apply(i, List.of(), LocalDateTime.now());
        return view(i);
    }

    public Map<String, Object> ignore(Long id, String note) {
        ReviewHubItem i = mine(id);
        if (i.getStatus() == Status.REPLIED) throw bad("Đánh giá này đã được trả lời");
        i.setStatus(Status.IGNORED);
        if (note != null && !note.isBlank()) i.setSellerNote(ReviewTextAnalyzer.cut(note.trim(), 500));
        return view(i);
    }

    public Map<String, Object> reopen(Long id) {
        ReviewHubItem i = mine(id);
        if (i.getStatus() == Status.REPLIED && i.getSource() == Source.SHOPPI) throw bad("Phản hồi đã đăng trên Shoppi, hãy sửa ở trang đánh giá");
        i.setStatus(i.getDraftReply() != null ? Status.DRAFTED : Status.NEW);
        i.setRepliedAt(null);
        ReviewPriority.apply(i, ReviewTextAnalyzer.analyze(i.getRating(), i.getContent()).severeHits(), LocalDateTime.now());
        return view(i);
    }

    /** The seller corrects the issues: kept by later analyses and used as training labels. */
    public Map<String, Object> setIssues(Long id, List<String> issues, String note) {
        ReviewHubItem i = mine(id);
        List<String> keys = issues == null ? List.of() : issues.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        if (keys.stream().anyMatch(k -> !ReviewTextAnalyzer.isIssue(k))) throw bad("Nhóm vấn đề không hợp lệ");
        i.setIssues(keys.isEmpty() ? null : String.join(",", keys));
        i.setIssuesManual(true);
        i.setAiPending(false);
        if (note != null) i.setSellerNote(blankToNull(ReviewTextAnalyzer.cut(note.trim(), 500)));
        ReviewPriority.apply(i, ReviewTextAnalyzer.analyze(i.getRating(), i.getContent()).severeHits(), LocalDateTime.now());
        return view(i);
    }

    // ==================== anomalies ====================

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> anomalies(String status, int page, int size) {
        Shop shop = myShop();
        AnomalyStatus s;
        try {
            s = status == null || status.isBlank() ? AnomalyStatus.FLAGGED : AnomalyStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw bad("Trạng thái không hợp lệ");
        }
        return PageResponse.of(repository.findByShopIdAndAnomalyStatusOrderByAnomalyScoreDescReviewedAtDesc(shop.getId(), s,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), this::view);
    }

    /** action: OK (a real review) | REPORT (Shoppi reviews go to the moderators) | RESET */
    public Map<String, Object> decideAnomaly(Long id, String action, String note) {
        ReviewHubItem i = mine(id);
        switch (action == null ? "" : action) {
            case "OK" -> i.setAnomalyStatus(AnomalyStatus.CONFIRMED_OK);
            case "REPORT" -> {
                i.setAnomalyStatus(AnomalyStatus.REPORTED);
                if (i.getSource() == Source.SHOPPI) {
                    Shop shop = myShop();
                    auditService.notifyAdmins(AdminPermission.PRODUCTS, "Shop báo cáo đánh giá bất thường",
                            shop.getName() + " báo cáo đánh giá " + i.getRating() + " sao của " + Objects.requireNonNullElse(i.getReviewerName(), "người mua")
                                    + " cho \"" + ReviewTextAnalyzer.cut(i.getProductName(), 60) + "\": "
                                    + Objects.requireNonNullElse(i.getAnomalyNote(), "") + (note == null || note.isBlank() ? "" : " · Shop ghi chú: " + note.trim()),
                            "/products/" + i.getProductId());
                }
            }
            case "RESET" -> i.setAnomalyStatus(i.getAnomalyScore() >= ReviewAnomalyDetector.FLAG_AT ? AnomalyStatus.FLAGGED : AnomalyStatus.NONE);
            default -> throw bad("Hành động không hợp lệ");
        }
        if (note != null && !note.isBlank()) i.setSellerNote(ReviewTextAnalyzer.cut(note.trim(), 500));
        return view(i);
    }

    // ==================== dashboard ====================

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Integer daysParam) {
        Shop shop = myShop();
        int days = daysParam == null ? 30 : Math.max(7, Math.min(daysParam, 365));
        LocalDateTime now = LocalDateTime.now(), from = now.minusDays(days), prevFrom = from.minusDays(days);
        List<ReviewHubItem> all = repository.since(shop.getId(), prevFrom);
        List<ReviewHubItem> cur = all.stream().filter(i -> !i.getReviewedAt().isBefore(from)).toList();
        List<ReviewHubItem> prev = all.stream().filter(i -> i.getReviewedAt().isBefore(from)).toList();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", cur.size());
        kpi.put("totalPrev", prev.size());
        kpi.put("avgRating", avg(cur));
        kpi.put("avgRatingPrev", avg(prev));
        kpi.put("negativePct", pct(cur.stream().filter(i -> i.getSentiment() == Sentiment.NEGATIVE).count(), cur.size()));
        kpi.put("positivePct", pct(cur.stream().filter(i -> i.getSentiment() == Sentiment.POSITIVE).count(), cur.size()));
        // what needs doing, whatever the period
        List<ReviewHubItem> open = openItems(shop.getId());
        kpi.put("needReply", open.stream().filter(i -> i.getRating() <= 3 || i.getPriorityLevel() == Level.URGENT || i.getPriorityLevel() == Level.HIGH).count());
        kpi.put("urgent", open.stream().filter(i -> i.getPriorityLevel() == Level.URGENT).count());
        kpi.put("overdue", open.stream().filter(i -> i.getRating() <= 3 && i.getReplyDueAt() != null && i.getReplyDueAt().isBefore(now)).count());
        List<ReviewHubItem> needAnswer = cur.stream().filter(i -> i.getRating() <= 3).toList();
        kpi.put("responseRate", pct(needAnswer.stream().filter(i -> i.getStatus() == Status.REPLIED).count(), needAnswer.size()));
        OptionalDouble hours = cur.stream().filter(i -> i.getRepliedAt() != null)
                .mapToLong(i -> Duration.between(i.getReviewedAt(), i.getRepliedAt()).toMinutes()).average();
        kpi.put("avgResponseHours", hours.isPresent() ? Math.round(hours.getAsDouble() / 6.0) / 10.0 : null);
        kpi.put("anomalies", repository.findByShopIdAndAnomalyStatusOrderByAnomalyScoreDescReviewedAtDesc(shop.getId(), AnomalyStatus.FLAGGED,
                PageRequest.of(0, 1)).getTotalElements());

        // per day (per week beyond 90 days)
        int step = days > 90 ? 7 : 1;
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate d = from.toLocalDate().plusDays(1); !d.isAfter(now.toLocalDate()); d = d.plusDays(step)) {
            LocalDate a = d, b = d.plusDays(step);
            List<ReviewHubItem> in = cur.stream().filter(i -> !i.getReviewedAt().toLocalDate().isBefore(a) && i.getReviewedAt().toLocalDate().isBefore(b)).toList();
            series.add(AdminMap.of("date", a.toString(), "count", in.size(), "negative", in.stream().filter(i -> i.getSentiment() == Sentiment.NEGATIVE).count(),
                    "avgRating", in.isEmpty() ? null : avg(in)));
        }

        Map<String, Long> issueCounts = new LinkedHashMap<>();
        ReviewTextAnalyzer.ISSUES.forEach(x -> issueCounts.put(x.key(), 0L));
        cur.stream().filter(i -> i.getIssues() != null && i.getSentiment() != Sentiment.POSITIVE)
                .flatMap(i -> Arrays.stream(i.getIssues().split(","))).forEach(k -> issueCounts.merge(k, 1L, Long::sum));
        long withIssue = cur.stream().filter(i -> i.getIssues() != null && i.getSentiment() != Sentiment.POSITIVE).count();
        List<Map<String, Object>> issues = issueCounts.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> AdminMap.of("key", e.getKey(), "label", ReviewTextAnalyzer.label(e.getKey()), "count", e.getValue(), "pct", pct(e.getValue(), withIssue)))
                .toList();

        List<Map<String, Object>> sources = Arrays.stream(Source.values()).map(s -> {
            List<ReviewHubItem> in = cur.stream().filter(i -> i.getSource() == s).toList();
            return in.isEmpty() ? null : AdminMap.of("source", s.name(), "count", in.size(), "avgRating", avg(in),
                    "negativePct", pct(in.stream().filter(i -> i.getSentiment() == Sentiment.NEGATIVE).count(), in.size()));
        }).filter(Objects::nonNull).toList();

        Map<String, Long> levels = new LinkedHashMap<>();
        for (Level l : Level.values()) levels.put(l.name(), open.stream().filter(i -> i.getPriorityLevel() == l).count());
        Map<Integer, Long> stars = new TreeMap<>(Comparator.reverseOrder());
        for (int s = 5; s >= 1; s--) {
            int st = s;
            stars.put(s, cur.stream().filter(i -> i.getRating() == st).count());
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", days);
        m.put("kpi", kpi);
        m.put("series", series);
        m.put("issues", issues);
        m.put("sources", sources);
        m.put("levels", levels);
        m.put("stars", stars);
        m.put("recurring", recurringClusters(shop, 30).stream().limit(3).toList());
        m.put("urgentItems", open.stream().filter(i -> i.getPriorityLevel() == Level.URGENT || i.getPriorityLevel() == Level.HIGH)
                .sorted(Comparator.comparingInt(ReviewHubItem::getPriority).reversed()).limit(5).map(this::view).toList());
        m.put("ai", AdminMap.of("llm", ai.llmAvailable(), "service", ai.serviceAvailable(),
                "pending", open.stream().filter(ReviewHubItem::isAiPending).count()));
        return m;
    }

    private List<ReviewHubItem> openItems(Long shopId) {
        return repository.findAll((root, q, cb) -> cb.and(cb.equal(root.get("shopId"), shopId), root.get("status").in(Status.NEW, Status.DRAFTED)));
    }

    // ==================== recurring issues ====================

    @Transactional(readOnly = true)
    public Map<String, Object> recurring(Integer daysParam) {
        Shop shop = myShop();
        int days = daysParam == null ? 30 : Math.max(7, Math.min(daysParam, 365));
        return Map.of("days", days, "clusters", recurringClusters(shop, days));
    }

    /** Complaints grouped by product and issue, the biggest and fastest growing first. */
    List<Map<String, Object>> recurringClusters(Shop shop, int days) {
        LocalDateTime now = LocalDateTime.now(), from = now.minusDays(days);
        List<ReviewHubItem> items = repository.since(shop.getId(), from.minusDays(days)).stream()
                .filter(i -> i.getIssues() != null && i.getSentiment() != Sentiment.POSITIVE).toList();
        record Key(String product, String issue) {
        }
        Map<Key, List<ReviewHubItem>> cur = new LinkedHashMap<>(), prev = new HashMap<>();
        for (ReviewHubItem i : items) {
            for (String issue : i.getIssues().split(",")) {
                Key k = new Key(ReviewAnomalyDetector.productKey(i), issue);
                (i.getReviewedAt().isBefore(from) ? prev : cur).computeIfAbsent(k, x -> new ArrayList<>()).add(i);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        cur.forEach((k, list) -> {
            if (list.size() < 2) return;
            int before = prev.getOrDefault(k, List.of()).size();
            list.sort(Comparator.comparing(ReviewHubItem::getReviewedAt).reversed());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k.product());
            m.put("productId", list.get(0).getProductId());
            m.put("productName", list.get(0).getProductName());
            m.put("issue", k.issue());
            m.put("issueLabel", ReviewTextAnalyzer.label(k.issue()));
            m.put("count", list.size());
            m.put("previous", before);
            m.put("trendPct", before == 0 ? null : Math.round((list.size() - before) * 100.0 / before));
            m.put("rising", list.size() > before);
            m.put("avgRating", avg(list));
            m.put("sources", list.stream().map(i -> i.getSource().name()).distinct().toList());
            m.put("firstSeen", list.get(list.size() - 1).getReviewedAt());
            m.put("lastSeen", list.get(0).getReviewedAt());
            m.put("samples", list.stream().map(i -> Objects.requireNonNullElse(i.getIssueSummary(), i.getContent())).filter(Objects::nonNull)
                    .distinct().limit(4).toList());
            m.put("itemIds", list.stream().map(ReviewHubItem::getId).limit(50).toList());
            m.put("fix", ReviewTextAnalyzer.fix(k.issue()));
            // likely a real product defect: quality/safety complaints repeating
            m.put("productDefect", Set.of("QUALITY", "AUTHENTICITY", "WRONG_DESCRIPTION").contains(k.issue()) && list.size() >= 3);
            out.add(m);
        });
        out.sort(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("count")).reversed()
                .thenComparing(m -> !(Boolean) m.get("rising")));
        return out;
    }

    /** The AI's reading of one cluster: likely cause and what to fix. */
    @Transactional(readOnly = true)
    public Map<String, Object> rootCause(String productKey, String issue, Integer days) {
        Shop shop = myShop();
        if (!ReviewTextAnalyzer.isIssue(issue)) throw bad("Nhóm vấn đề không hợp lệ");
        Map<String, Object> cluster = recurringClusters(shop, days == null ? 90 : days).stream()
                .filter(c -> c.get("key").equals(productKey) && c.get("issue").equals(issue)).findFirst()
                .orElseThrow(() -> new ApiException("Không tìm thấy nhóm vấn đề này", HttpStatus.NOT_FOUND));
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) cluster.get("itemIds");
        List<String> texts = repository.findByShopIdAndIdIn(shop.getId(), ids).stream().map(ReviewHubItem::getContent)
                .filter(t -> t != null && !t.isBlank()).toList();
        Map<String, Object> r = new LinkedHashMap<>(ai.rootCause(String.valueOf(cluster.get("productName")), issue, texts));
        r.put("count", cluster.get("count"));
        return r;
    }

    // ==================== AI service (admin) ====================

    @Transactional(readOnly = true)
    public Map<String, Object> serviceStatus() {
        Map<String, Object> m = new LinkedHashMap<>(ai.serviceHealth());
        m.put("labelled", repository.labeled().size());
        m.put("llm", ai.llmAvailable());
        return m;
    }

    /** Trains the FastAPI service's classifier on the reviews labelled by sellers and by the LLM. */
    public Map<String, Object> train() {
        List<ReviewHubItem> labelled = repository.labeled();
        if (labelled.size() < 20) throw bad("Cần ít nhất 20 đánh giá đã gán nhãn (do AI hoặc người bán sửa), hiện có " + labelled.size());
        try {
            Map<String, Object> r = ai.train(labelled);
            auditService.log("TRAIN_REVIEW_MODEL", "SETTING", null, "Mô hình phân loại đánh giá", "Huấn luyện trên " + labelled.size() + " đánh giá");
            return r;
        } catch (IllegalStateException e) {
            throw new ApiException(e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ==================== helpers ====================

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private ReviewHubItem mine(Long id) {
        Shop shop = myShop();
        return repository.findById(id).filter(i -> i.getShopId().equals(shop.getId()))
                .orElseThrow(() -> new ApiException("Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
    }

    private static Source source(String s) {
        try {
            return Source.valueOf(s == null ? "" : s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Sàn không hợp lệ (SHOPEE, LAZADA, TIKI, TIKTOK, OTHER)");
        }
    }

    private static double avg(List<ReviewHubItem> l) {
        return l.isEmpty() ? 0 : Math.round(l.stream().mapToInt(ReviewHubItem::getRating).average().orElse(0) * 100) / 100.0;
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static boolean has(Map<String, String> f, String k) {
        return f.get(k) != null && !f.get(k).isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

    /** LinkedHashMap from key/value pairs, null values allowed. */
    static final class AdminMap {
        static Map<String, Object> of(Object... kv) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int k = 0; k + 1 < kv.length; k += 2) m.put((String) kv[k], kv[k + 1]);
            return m;
        }
    }
}
