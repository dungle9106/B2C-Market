package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.ChatbotResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.ChatHistory;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.repository.ChatHistoryRepository;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// The RAG pipeline for the buyer-facing shopping assistant:
//   1. Retrieval: understand the question (keywords without accents, price range,
//      "where is my order", policy topics) and pull matching products / the
//      buyer's recent orders from Postgres
//   2. Augmentation: fold those, the platform's policies and the last few turns
//      of the conversation into the system prompt as "context"
//   3. Generation: call the LLM (via AIService) constrained to that context
// When no LLM is configured a rule-based answer is built from the same context,
// so the feature still works in a demo without an API key.
@Slf4j
@Service
public class ChatbotService {

    private static final int MAX_CONTEXT_PRODUCTS = 5;
    private static final int HISTORY_TURNS = 6;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final TransactionTemplate readTx;
    private final com.marketplace.backend.service.search.SemanticSearchService semanticSearch;

    public ChatbotService(ProductRepository productRepository, OrderRepository orderRepository,
                          ChatHistoryRepository chatHistoryRepository, UserRepository userRepository,
                          AIService aiService, PlatformTransactionManager txManager,
                          com.marketplace.backend.service.search.SemanticSearchService semanticSearch) {
        this.semanticSearch = semanticSearch;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    // what the retrieval step found; built inside a short read-only transaction so the
    // (slow) LLM call below doesn't hold a database connection
    private record Context(String normalized, List<Product> products, List<ProductResponse> productCards,
                          List<Order> orders, List<ChatbotResponse.OrderRef> orderRefs,
                          String ordersText, List<ChatHistory> history, Topic topic, boolean wantsHuman) {}

    public ChatbotResponse chat(String userMessage) {
        Long userId = CurrentUser.id();
        String message = userMessage.trim();
        Context ctx = readTx.execute(s -> retrieve(userId, message));

        String reply = aiService.isConfigured()
                ? aiService.complete(buildSystemPrompt(ctx), message)
                : ruleBasedReply(ctx);

        // best-effort logging of the conversation turn (also feeds the next turn's context)
        ChatHistory saved = null;
        try {
            saved = chatHistoryRepository.save(ChatHistory.builder()
                    .user(userRepository.getReferenceById(userId))
                    .message(NotificationService.cut(message, 2000))
                    .response(NotificationService.cut(reply, 4000))
                    .build());
        } catch (Exception e) {
            log.warn("Could not save chatbot history: {}", e.getMessage());
        }

        boolean couldNotHelp = ctx.products().isEmpty() && ctx.orders().isEmpty() && ctx.topic() == null;
        return new ChatbotResponse(reply, ctx.productCards(), ctx.orderRefs(),
                ctx.wantsHuman() || couldNotHelp, saved != null ? saved.getCreatedAt() : null);
    }

    /** Previous turns, oldest first, to restore the chat window. */
    public List<Map<String, Object>> history() {
        List<ChatHistory> turns = new ArrayList<>(chatHistoryRepository.findTop30ByUserIdOrderByCreatedAtDesc(CurrentUser.id()));
        Collections.reverse(turns);
        return turns.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("message", h.getMessage());
            m.put("reply", h.getResponse());
            m.put("createdAt", h.getCreatedAt());
            return m;
        }).toList();
    }

    public void clearHistory() {
        chatHistoryRepository.deleteByUserId(CurrentUser.id());
    }

    // ==================== 1. retrieval ====================

    private Context retrieve(Long userId, String message) {
        String norm = TextNormalizer.normalize(message);
        PriceRange price = parsePrice(norm);
        Topic topic = Topic.detect(norm);

        // topic phrases are not product words: "don hang dang o dau" must not match "dau goi"
        List<Product> products = findProducts(Topic.strip(stripPrice(norm)), price, topic);
        // RAG, step 2: too few keyword matches ("quà cho mẹ", "đồ đi biển"): add the products closest in meaning
        // (embeddings + pgvector), still within the price range
        if (products.size() < MAX_CONTEXT_PRODUCTS && topic == null) {
            Map<Long, Product> merged = new LinkedHashMap<>();
            products.forEach(p -> merged.put(p.getId(), p));
            for (Product p : semanticSearch.nearestProducts(message, 10)) {
                if (merged.size() >= MAX_CONTEXT_PRODUCTS) break;
                if (price == null || price.contains(p.getEffectivePrice())) merged.putIfAbsent(p.getId(), p);
            }
            products = List.copyOf(merged.values());
        }

        List<Order> orders = List.of();
        if (topic == Topic.ORDER || topic == Topic.RETURN || topic == Topic.CANCEL) {
            orders = orderRepository.findTop5ByBuyerIdOrderByCreatedAtDesc(userId);
        }
        String ordersText = describeOrders(orders);
        List<ChatbotResponse.OrderRef> refs = orders.stream()
                .map(o -> new ChatbotResponse.OrderRef(o.getId(), o.getCode(), o.getStatus().name(), o.getShop().getName()))
                .toList();

        List<ChatHistory> history = new ArrayList<>(chatHistoryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(HISTORY_TURNS).toList());
        Collections.reverse(history);

        boolean wantsHuman = containsAny(norm, "nhan vien", "gap nguoi", "tong dai", "khieu nai", "to cao", "lua dao", "ho tro truc tiep");
        return new Context(norm, products, products.stream().map(ProductResponse::from).toList(),
                orders, refs, ordersText, history, topic, wantsHuman);
    }

    // common words that say nothing about *which* product is wanted (accent-free). Words whose
    // accent-free form is also a product word are kept out on purpose: "goi" (dau goi), "do" (ao do),
    // "da" (tui da), "vang" (day chuyen vang), "dau" (dau an), "hop" (hop qua)
    private static final Set<String> STOPWORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "shop", "ad", "admin", "oi", "a", "nhe", "nha", "ha", "ah", "vay", "the",
            "can", "muon", "tim", "kiem", "mua", "xem", "cho", "co", "khong", "ko", "k", "nao", "gi", "la", "va", "voi", "cua",
            "duoc", "giup", "y", "tu", "van", "gia", "re", "dat", "dep", "tot", "nhat", "loai", "mot", "cai", "chiec",
            "doi", "bo", "nhung", "cac", "hay", "di", "ve", "de", "thi", "nay", "dang", "nhu", "ra", "sao", "bao", "nhieu",
            "hang", "san", "pham", "sp", "xin", "chao", "hello", "hi", "duoi", "tren", "khoang", "tam", "nghin", "ngan", "trieu",
            "tr", "vnd", "dong", "d", "con", "khac", "them", "nua", "lam", "ai", "o", "nen", "hon", "ma", "thich",
            "phu", "ok", "oke", "uh", "roi", "chua", "gium", "dum", "lay", "chay", "moi", "don");

    private List<Product> findProducts(String norm, PriceRange price, Topic topic) {
        List<String> words = Arrays.stream(norm.split("[^a-z0-9]+"))
                .filter(w -> !w.isBlank() && !STOPWORDS.contains(w) && !w.matches("\\d+"))
                .distinct().limit(6).toList();

        if (words.isEmpty()) {
            // "goi y vai mon duoi 200k", "san pham ban chay": best sellers within the range
            boolean browsing = price != null || containsAny(norm, "ban chay", "goi y", "noi bat", "hot", "pho bien");
            if (!browsing || topic != null) return List.of();
            return productRepository.findTop10ByActiveTrueOrderBySoldCountDesc().stream()
                    .filter(p -> p.getShop().isActive() && (price == null || price.contains(p.getEffectivePrice())))
                    .limit(MAX_CONTEXT_PRODUCTS).toList();
        }

        // candidates: every product containing at least one keyword as a whole word
        Map<Long, Product> candidates = new LinkedHashMap<>();
        for (String w : words) {
            for (Product p : productRepository.findForChatbot("%" + w + "%", PageRequest.of(0, 40))) {
                candidates.putIfAbsent(p.getId(), p);
            }
        }
        // score: matched words, plus a bonus for adjacent pairs ("ao thun" beats "ao" + "thun" apart)
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < words.size(); i++) pairs.add(words.get(i) + " " + words.get(i + 1));

        record Scored(Product p, int score) {}
        return candidates.values().stream()
                .filter(p -> price == null || price.contains(p.getEffectivePrice()))
                .map(p -> {
                    String text = " " + (p.getSearchText() != null ? p.getSearchText() : TextNormalizer.normalize(p.getName())) + " ";
                    int score = 0;
                    for (String w : words) if (text.contains(" " + w + " ")) score += 2;
                    for (String pair : pairs) if (text.contains(" " + pair + " ")) score += 3;
                    return new Scored(p, score);
                })
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> s.p().getSoldCount(), Comparator.reverseOrder()))
                .limit(MAX_CONTEXT_PRODUCTS)
                .map(Scored::p)
                .toList();
    }

    // ---- price understanding: "duoi 200k", "tu 1 den 2 trieu", "tam 500 nghin" ----

    private record PriceRange(BigDecimal min, BigDecimal max) {
        boolean contains(BigDecimal v) {
            return (min == null || v.compareTo(min) >= 0) && (max == null || v.compareTo(max) <= 0);
        }

        String describe() {
            if (min != null && max != null) return "từ " + NotificationService.vnd(min) + " đến " + NotificationService.vnd(max);
            if (max != null) return "dưới " + NotificationService.vnd(max);
            return "từ " + NotificationService.vnd(min);
        }
    }

    // "1.500.000" (thousands separators) or "1,5" / "1.5" (decimals, with a unit)
    private static final String AMOUNT = "(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*(k|nghin|ngan|tr|trieu|cu)?";
    private static final Pattern BETWEEN = Pattern.compile("(?:tu|khoang)\\s*" + AMOUNT + "\\s*(?:den|toi|-)\\s*" + AMOUNT);
    private static final Pattern MAX = Pattern.compile("(?:duoi|nho hon|it hon|khong qua|toi da|re hon|<)\\s*" + AMOUNT);
    private static final Pattern MIN = Pattern.compile("(?:tren|lon hon|nhieu hon|it nhat|toi thieu|>)\\s*" + AMOUNT);
    private static final Pattern AROUND = Pattern.compile("(?:tam|khoang|gia)\\s*" + AMOUNT);

    private static PriceRange parsePrice(String norm) {
        Matcher m = BETWEEN.matcher(norm);
        if (m.find()) {
            // "tu 1 den 2 trieu": the unit written once applies to both numbers
            String unit = m.group(4) != null ? m.group(4) : m.group(2);
            return new PriceRange(amount(m.group(1), m.group(2) != null ? m.group(2) : unit), amount(m.group(3), unit));
        }
        BigDecimal max = null, min = null;
        if ((m = MAX.matcher(norm)).find()) max = amount(m.group(1), m.group(2));
        if ((m = MIN.matcher(norm)).find()) min = amount(m.group(1), m.group(2));
        if (max != null || min != null) return new PriceRange(min, max);
        if ((m = AROUND.matcher(norm)).find() && m.group(2) != null) {
            BigDecimal mid = amount(m.group(1), m.group(2));
            return new PriceRange(mid.multiply(BigDecimal.valueOf(0.7)), mid.multiply(BigDecimal.valueOf(1.3)));
        }
        return null;
    }

    private static BigDecimal amount(String number, String unit) {
        boolean grouped = unit == null && number.matches("\\d{1,3}([.,]\\d{3})+");
        BigDecimal n = new BigDecimal(grouped ? number.replaceAll("[.,]", "") : number.replace(',', '.'));
        if (unit == null) {
            // a bare "duoi 200" almost always means thousands of VND
            return n.compareTo(BigDecimal.valueOf(1000)) < 0 ? n.multiply(BigDecimal.valueOf(1000)) : n;
        }
        return switch (unit) {
            case "tr", "trieu", "cu" -> n.multiply(BigDecimal.valueOf(1_000_000));
            default -> n.multiply(BigDecimal.valueOf(1000));
        };
    }

    private static String stripPrice(String norm) {
        for (Pattern p : List.of(BETWEEN, MAX, MIN, AROUND)) norm = p.matcher(norm).replaceAll(" ");
        return norm;
    }

    // ---- topics the platform's policies answer ----

    private enum Topic {
        ORDER("don hang", "don cua", "ma don", "van don", "giao chua", "bao gio nhan", "khi nao nhan", "dang o dau", "trang thai don", "theo doi don", "giao den dau"),
        CANCEL("huy don", "huy dat", "muon huy"),
        RETURN("tra hang", "hoan tien", "doi hang", "doi tra", "bi loi", "hang loi", "giao sai", "thieu hang"),
        SHIPPING("phi ship", "van chuyen", "giao hang", "freeship", "mien phi van chuyen", "phi giao", "hoa toc", "bao lau"),
        PAYMENT("thanh toan", "tra tien", "cod", "chuyen khoan", "the tin dung", "momo", "zalopay", "vnpay", "vi dien tu"),
        VOUCHER("voucher", "ma giam", "khuyen mai", "giam gia", "coupon", "flash sale"),
        ACCOUNT("tai khoan", "dang nhap", "mat khau", "dang ky", "ban hang", "mo shop", "nguoi ban");

        private final String[] keys;

        Topic(String... keys) { this.keys = keys; }

        static Topic detect(String norm) {
            for (Topic t : values()) if (containsAny(norm, t.keys)) return t;
            return null;
        }

        static String strip(String norm) {
            String s = " " + norm.replaceAll("[^a-z0-9]+", " ") + " "; // "o dau?" -> "o dau"
            for (Topic t : values()) for (String k : t.keys) s = s.replace(" " + k + " ", " ");
            return s.trim();
        }
    }

    // Shoppi's rules, given to the LLM as ground truth (and used for the rule-based answers)
    private static final Map<Topic, String> POLICY = new EnumMap<>(Map.of(
            Topic.SHIPPING, "Vận chuyển: Shoppi hợp tác với GHN, GHTK, J&T Express, Viettel Post. Có 3 gói: Tiết kiệm, Nhanh, "
                    + "Hỏa tốc (Hỏa tốc chỉ khi shop và người nhận cùng tỉnh). Phí ship tính theo cùng tỉnh/khác tỉnh. "
                    + "Sản phẩm có nhãn Freeship được miễn phí vận chuyển tối đa bằng phí gói Tiết kiệm; ngoài ra có voucher giảm phí ship.",
            Topic.PAYMENT, "Thanh toán: COD (trả tiền khi nhận hàng), ví điện tử (MoMo, ZaloPay, VNPay), thẻ Visa/Mastercard/JCB, "
                    + "chuyển khoản ngân hàng. Đơn thanh toán online phải hoàn tất trong 15 phút, quá hạn đơn tự hủy.",
            Topic.CANCEL, "Hủy đơn: người mua tự hủy được khi đơn đang Chờ thanh toán, Chờ xác nhận hoặc Chờ lấy hàng "
                    + "(vào Đơn hàng của tôi > chọn đơn > Hủy đơn). Khi shop đã giao cho đơn vị vận chuyển thì không hủy được nữa. "
                    + "Đơn đã thanh toán online sẽ được hoàn tiền, voucher được trả lại.",
            Topic.RETURN, "Trả hàng/Hoàn tiền: yêu cầu trong thời hạn đổi trả của sản phẩm (ghi trên trang sản phẩm), tính từ lúc giao thành công. "
                    + "Vào chi tiết đơn hàng > Trả hàng/Hoàn tiền, chọn sản phẩm, lý do và tải ảnh/video bằng chứng. "
                    + "Shop đồng ý thì hoàn tiền; nếu shop từ chối, người mua có thể Khiếu nại để Shoppi phân xử.",
            Topic.VOUCHER, "Voucher: có voucher của từng shop và Shoppi Voucher dùng cho toàn sàn; loại giảm %, giảm tiền hoặc giảm phí ship, "
                    + "có điều kiện đơn tối thiểu và hạn dùng. Chọn voucher ở bước Thanh toán. Thông báo khuyến mãi mới nằm ở chuông Thông báo.",
            Topic.ACCOUNT, "Tài khoản: đăng ký bằng email hoặc đăng nhập Google. Muốn bán hàng thì vào \"Trở Thành Người Bán\" để mở shop. "
                    + "Vấn đề tài khoản khác hãy gửi yêu cầu ở Trung tâm hỗ trợ.",
            Topic.ORDER, "Đơn hàng: xem ở mục Đơn hàng của tôi; chi tiết đơn có tiến trình và hành trình vận chuyển (mã vận đơn khi shop đã giao cho ĐVVC). "
                    + "Có thể nhắn tin cho shop bằng nút Chat với Shop."));

    // ==================== 2. augmentation ====================

    private String buildSystemPrompt(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý tư vấn mua sắm của Shoppi, một sàn thương mại điện tử đa người bán.\n");
        sb.append("QUY TẮC BẮT BUỘC:\n");
        sb.append("- Chỉ tư vấn sản phẩm có trong mục SẢN PHẨM bên dưới; KHÔNG bịa tên, giá, tính năng hay tồn kho.\n");
        sb.append("- Câu hỏi về đơn hàng chỉ trả lời dựa trên mục ĐƠN HÀNG CỦA KHÁCH; về quy định dựa trên mục CHÍNH SÁCH.\n");
        sb.append("- Nếu không có thông tin phù hợp, nói rõ và gợi ý khách mô tả cụ thể hơn (loại hàng, khoảng giá), "
                + "nhắn tin cho shop, hoặc gửi yêu cầu ở Trung tâm hỗ trợ.\n");
        sb.append("- Trả lời ngắn gọn (tối đa khoảng 120 từ), thân thiện, bằng tiếng Việt có dấu. Có thể gạch đầu dòng. "
                + "Khi nhắc sản phẩm, ghi đúng tên và giá. Thẻ sản phẩm sẽ được hiển thị bên dưới câu trả lời.\n\n");

        sb.append("CHÍNH SÁCH SHOPPI:\n");
        POLICY.values().forEach(p -> sb.append("- ").append(p).append("\n"));
        sb.append("\n");

        if (ctx.products().isEmpty()) {
            sb.append("SẢN PHẨM: không tìm thấy sản phẩm nào phù hợp với câu hỏi này.\n");
        } else {
            sb.append("SẢN PHẨM liên quan tìm được:\n");
            int i = 1;
            for (Product p : ctx.products()) {
                sb.append(i++).append(". ").append(p.getName())
                        .append(" - Giá: ").append(NotificationService.vnd(p.getEffectivePrice()))
                        .append(p.isFlashSaleActive() ? " (đang Flash Sale)" : "")
                        .append(" - Đã bán: ").append(p.getSoldCount())
                        .append(p.getRatingAvg() != null && p.getRatingCount() != null && p.getRatingCount() > 0
                                ? " - Đánh giá: " + String.format("%.1f", p.getRatingAvg()) + "/5" : "")
                        .append(p.getStock() != null && p.getStock() <= 0 ? " - HẾT HÀNG" : "")
                        .append(p.isFreeShipping() ? " - Freeship" : "")
                        .append(" - Shop: ").append(p.getShop().getName())
                        .append(p.getShop().getProvince() != null ? " (" + p.getShop().getProvince() + ")" : "")
                        .append(" - Mô tả: ").append(shorten(p.getDescription()))
                        .append("\n");
            }
        }
        if (!ctx.ordersText().isEmpty()) {
            sb.append("\nĐƠN HÀNG CỦA KHÁCH (mới nhất trước):\n").append(ctx.ordersText());
        }
        if (!ctx.history().isEmpty()) {
            sb.append("\nLỊCH SỬ HỘI THOẠI GẦN ĐÂY (để hiểu các câu hỏi nối tiếp):\n");
            for (ChatHistory h : ctx.history()) {
                sb.append("Khách: ").append(shorten(h.getMessage())).append("\n");
                sb.append("Trợ lý: ").append(shorten(h.getResponse())).append("\n");
            }
        }
        return sb.toString();
    }

    private String describeOrders(List<Order> orders) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (Order o : orders) {
            sb.append("- ").append(o.getCode())
                    .append(" | Shop: ").append(o.getShop().getName())
                    .append(" | ").append(o.getItems().stream().map(i -> i.getProductNameSnapshot() + " x" + i.getQuantity())
                            .collect(Collectors.joining(", ")))
                    .append(" | Trạng thái: ").append(statusName(o.getStatus()))
                    .append(" | Tổng: ").append(NotificationService.vnd(o.getTotal()))
                    .append(" | Đặt ngày ").append(o.getCreatedAt().format(f));
            if (o.getTrackingNumber() != null) sb.append(" | Mã vận đơn: ").append(o.getTrackingNumber());
            if (o.getStatus() != OrderStatus.DELIVERED && o.getEstimatedDeliveryTo() != null) {
                sb.append(" | Dự kiến giao trước ").append(o.getEstimatedDeliveryTo().format(f));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== fallback without an LLM ====================

    private String ruleBasedReply(Context ctx) {
        StringBuilder sb = new StringBuilder();
        if (!ctx.orders().isEmpty()) {
            sb.append("Đây là các đơn hàng gần đây của bạn:\n");
            for (Order o : ctx.orders()) {
                sb.append("• ").append(o.getCode()).append(" (").append(o.getShop().getName()).append("): ")
                        .append(statusName(o.getStatus()));
                if (o.getTrackingNumber() != null) sb.append(" – mã vận đơn ").append(o.getTrackingNumber());
                sb.append("\n");
            }
        }
        if (ctx.topic() != null && ctx.topic() != Topic.ORDER) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(POLICY.get(ctx.topic())).append("\n");
        } else if (ctx.topic() == Topic.ORDER && ctx.orders().isEmpty()) {
            sb.append("Bạn chưa có đơn hàng nào. ").append(POLICY.get(Topic.ORDER)).append("\n");
        }
        if (!ctx.products().isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            PriceRange price = parsePrice(ctx.normalized());
            sb.append("Mình tìm được ").append(ctx.products().size()).append(" sản phẩm phù hợp")
                    .append(price != null ? " (giá " + price.describe() + ")" : "").append(":\n");
            for (Product p : ctx.products()) {
                sb.append("• ").append(p.getName()).append(" – ").append(NotificationService.vnd(p.getEffectivePrice()))
                        .append(" (").append(p.getShop().getName()).append(")\n");
            }
            sb.append("Bấm vào thẻ sản phẩm bên dưới để xem chi tiết nhé!");
        }
        if (sb.isEmpty()) {
            return "Mình chưa tìm thấy thông tin phù hợp. Bạn thử mô tả cụ thể hơn, ví dụ: \"áo thun nam dưới 200k\", "
                    + "\"phí ship thế nào\", \"đơn hàng của tôi đang ở đâu\". Nếu cần người hỗ trợ trực tiếp, "
                    + "bạn có thể gửi yêu cầu tại Trung tâm hỗ trợ.";
        }
        return sb.toString().trim();
    }

    // ==================== helpers ====================

    private static String statusName(OrderStatus s) {
        return switch (s) {
            case AWAITING_PAYMENT -> "Chờ thanh toán";
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Chờ lấy hàng (shop đang chuẩn bị)";
            case SHIPPING -> "Đang giao";
            case DELIVERED -> "Đã giao";
            case CANCELLED -> "Đã hủy";
            case RETURN_REQUESTED -> "Đang yêu cầu trả hàng/hoàn tiền";
            case REFUNDED -> "Đã hoàn tiền";
        };
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    private String shorten(String text) {
        if (text == null) return "";
        return text.length() > 150 ? text.substring(0, 150) + "..." : text;
    }
}
