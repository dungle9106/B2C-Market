package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.request.AddToCartRequest;
import com.marketplace.backend.dto.response.ChatbotResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.service.ai.Agent;
import com.marketplace.backend.service.reco.RecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The shopping assistant that can act ("agent", LLM function calling), used by ChatbotService when the AI provider
 * supports tools (OpenAI, Claude). Instead of being handed a fixed context, the model decides which tools to call:
 *
 *   search_products, get_product, recommend_for_me (the trained recommendation model), similar_products,
 *   my_orders, my_vouchers, add_to_cart, propose_cancel_order, handoff_to_human
 *
 * Every tool works only on the signed-in buyer's own data, with the same services (and rules) as the app.
 * Adding to the cart is done right away (easy to undo); cancelling an order is never done by the model: it only
 * shows a button, and the buyer confirms.
 */
@Slf4j
@Service
public class ShoppingAgent {

    static final int MAX_STEPS = 6;
    private static final int HISTORY_TURNS = 6;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AIService ai;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final VoucherWalletService walletService;
    private final RecommendationService recommendationService;
    private final TransactionTemplate readTx;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public ShoppingAgent(AIService ai, ProductRepository productRepository, OrderRepository orderRepository, CartService cartService,
                         VoucherWalletService walletService, RecommendationService recommendationService, PlatformTransactionManager txManager) {
        this.ai = ai;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.walletService = walletService;
        this.recommendationService = recommendationService;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /** The keyword + meaning product search of ChatbotService (runs inside the caller's transaction). */
    @FunctionalInterface
    interface ProductSearch {
        List<Product> search(String query, BigDecimal min, BigDecimal max, int limit);
    }

    /** actions: what the UI shows as buttons / notes ({type: CART_ADDED | CANCEL_ORDER, ...}); steps: the tools used. */
    public record Result(String reply, List<ProductResponse> products, List<ChatbotResponse.OrderRef> orders,
                         List<Map<String, Object>> actions, List<String> steps, boolean suggestSupport) {}

    public boolean available() {
        return ai.supportsTools();
    }

    /** The answer, or null when the model failed (the caller then answers the old way). */
    Result run(Long userId, String message, List<ChatHistory> history, ProductSearch search) {
        List<Agent.Msg> messages = new ArrayList<>();
        history.stream().skip(Math.max(0, history.size() - HISTORY_TURNS)).forEach(h -> {
            messages.add(new Agent.User(cut(h.getMessage(), 500)));
            messages.add(new Agent.Assistant(cut(h.getResponse(), 700), List.of()));
        });
        messages.add(new Agent.User(message));

        Run run = new Run(userId, search);
        for (int step = 0; step < MAX_STEPS; step++) {
            Agent.Reply reply = ai.converse(systemPrompt(), messages, TOOLS);
            if (reply.error() != null) {
                log.warn("Shopping agent: model call failed: {}", reply.error());
                return null;
            }
            if (reply.calls().isEmpty()) {
                if (reply.text() == null || reply.text().isBlank()) return null;
                return run.result(reply.text().trim());
            }
            messages.add(new Agent.Assistant(reply.text(), reply.calls()));
            run.round++;
            for (Agent.Call call : reply.calls()) {
                messages.add(new Agent.ToolResult(call.id(), call.name(), run.execute(call)));
            }
        }
        log.warn("Shopping agent: no answer after {} steps", MAX_STEPS);
        return null;
    }

    // ==================== the tools offered to the model ====================

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties, "required", List.of(required));
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    static final List<Agent.Tool> TOOLS = List.of(
            new Agent.Tool("search_products",
                    "Tìm sản phẩm đang bán trên Shoppi theo từ khóa (hiểu cả tiếng Việt không dấu và ý nghĩa gần đúng), có thể lọc theo khoảng giá.",
                    schema(Map.of(
                            "query", prop("string", "Loại hàng cần tìm, ví dụ \"áo thun nam\", \"tai nghe bluetooth\", \"quà cho mẹ\""),
                            "min_price", prop("number", "Giá thấp nhất (VND), bỏ trống nếu không có"),
                            "max_price", prop("number", "Giá cao nhất (VND), bỏ trống nếu không có"),
                            "sort", Map.of("type", "string", "enum", List.of("relevance", "price_asc", "price_desc", "best_selling", "rating"),
                                    "description", "Cách sắp xếp, mặc định relevance")), "query")),
            new Agent.Tool("get_product",
                    "Xem chi tiết một sản phẩm: mô tả, các phân loại (màu/size) với giá và tồn kho riêng, thời hạn đổi trả.",
                    schema(Map.of("product_id", prop("integer", "id sản phẩm lấy từ kết quả công cụ khác")), "product_id")),
            new Agent.Tool("recommend_for_me",
                    "Gợi ý sản phẩm hợp với khách này, từ mô hình gợi ý được huấn luyện trên hành vi mua sắm (đã xem, đã thích, đã mua). "
                            + "Dùng khi khách nhờ gợi ý chung chung mà không nói rõ loại hàng.",
                    schema(Map.of())),
            new Agent.Tool("similar_products",
                    "Sản phẩm thường được cùng nhóm người quan tâm / mua kèm với một sản phẩm, và sản phẩm cùng loại.",
                    schema(Map.of("product_id", prop("integer", "id sản phẩm")), "product_id")),
            new Agent.Tool("my_orders",
                    "Các đơn hàng gần đây của khách: mã đơn, sản phẩm, trạng thái, mã vận đơn, ngày giao dự kiến, có hủy được không.",
                    schema(Map.of("status", Map.of("type", "string",
                            "enum", List.of("ALL", "AWAITING_PAYMENT", "PENDING", "CONFIRMED", "SHIPPING", "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "REFUNDED"),
                            "description", "Lọc theo trạng thái, mặc định ALL")))),
            new Agent.Tool("my_vouchers",
                    "Voucher trong Kho Voucher của khách còn dùng được, và voucher đang phát có thể lưu.",
                    schema(Map.of())),
            new Agent.Tool("add_to_cart",
                    "Thêm sản phẩm vào giỏ hàng của khách. CHỈ dùng khi khách yêu cầu rõ ràng. Sản phẩm có phân loại thì bắt buộc variant_id "
                            + "(lấy từ get_product, do khách chọn).",
                    schema(Map.of(
                            "product_id", prop("integer", "id sản phẩm"),
                            "variant_id", prop("integer", "id phân loại, chỉ khi sản phẩm có phân loại"),
                            "quantity", prop("integer", "số lượng, mặc định 1")), "product_id")),
            new Agent.Tool("propose_cancel_order",
                    "Chuẩn bị hủy một đơn hàng của khách: hiện nút xác nhận để KHÁCH tự bấm. Đơn chưa bị hủy cho tới khi khách bấm nút.",
                    schema(Map.of(
                            "order_code", prop("string", "mã đơn hàng, lấy từ my_orders"),
                            "reason", prop("string", "lý do hủy theo lời khách")), "order_code")),
            new Agent.Tool("handoff_to_human",
                    "Đề nghị khách liên hệ nhân viên (chat với shop hoặc gửi yêu cầu hỗ trợ) khi việc nằm ngoài khả năng của trợ lý, "
                            + "khách bức xúc, khiếu nại hoặc muốn gặp người thật.",
                    schema(Map.of("reason", prop("string", "lý do ngắn gọn")))));

    private static final Map<String, String> STEP_NAMES = Map.of(
            "search_products", "Tìm sản phẩm", "get_product", "Xem chi tiết sản phẩm", "recommend_for_me", "Gợi ý bằng mô hình AI",
            "similar_products", "Tìm sản phẩm tương tự", "my_orders", "Xem đơn hàng", "my_vouchers", "Kiểm tra Kho Voucher",
            "add_to_cart", "Thêm vào giỏ hàng", "propose_cancel_order", "Chuẩn bị hủy đơn", "handoff_to_human", "Chuyển nhân viên hỗ trợ");

    private String systemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý mua sắm của Shoppi, một sàn thương mại điện tử đa người bán. Hôm nay là ")
                .append(LocalDate.now().format(DAY)).append(".\n\n");
        sb.append("CÁCH LÀM VIỆC:\n");
        sb.append("- Mọi thông tin về sản phẩm, giá, tồn kho, đơn hàng, voucher phải lấy từ công cụ; không bịa tên, giá hay tính năng.\n");
        sb.append("- Khách tìm một loại hàng: search_products (có thể gọi vài lần với từ khóa khác nhau, ví dụ khi tìm quà). "
                + "Khách nhờ gợi ý chung chung (\"có gì hợp với tôi\", \"gợi ý cho tôi\"): recommend_for_me.\n");
        sb.append("- Thêm vào giỏ chỉ khi khách yêu cầu rõ. Sản phẩm có phân loại mà khách chưa chọn: gọi get_product rồi hỏi khách chọn, "
                + "không tự chọn. \"Cái thứ hai\", \"cái rẻ nhất\" là theo danh sách vừa đưa ra; không chắc thì hỏi lại.\n");
        sb.append("- Hủy đơn: luôn dùng propose_cancel_order, rồi nói khách bấm nút xác nhận bên dưới. Không bao giờ nói là đã hủy.\n");
        sb.append("- Việc trợ lý không làm được (đổi địa chỉ, khiếu nại, đổi trả cụ thể, thanh toán...): hướng dẫn các bước trong ứng dụng "
                + "theo CHÍNH SÁCH, hoặc handoff_to_human.\n");
        sb.append("- Trả lời bằng tiếng Việt có dấu, ngắn gọn (tối đa khoảng 120 từ), thân thiện, có thể gạch đầu dòng. "
                + "Nhắc sản phẩm thì ghi đúng tên và giá. Thẻ sản phẩm và đơn hàng tự hiện bên dưới câu trả lời, không cần ghi đường link hay id.\n\n");
        sb.append("CHÍNH SÁCH SHOPPI:\n");
        ChatbotService.POLICY.values().forEach(p -> sb.append("- ").append(p).append("\n"));
        return sb.toString();
    }

    // ==================== one conversation turn ====================

    private final class Run {
        final Long userId;
        final ProductSearch search;
        int round;
        int productsRound = -1;
        final LinkedHashMap<Long, ProductResponse> shown = new LinkedHashMap<>();
        final LinkedHashMap<Long, ChatbotResponse.OrderRef> orders = new LinkedHashMap<>();
        final List<Map<String, Object>> actions = new ArrayList<>();
        final LinkedHashSet<String> steps = new LinkedHashSet<>();
        boolean support;

        Run(Long userId, ProductSearch search) {
            this.userId = userId;
            this.search = search;
        }

        Result result(String reply) {
            return new Result(reply, List.copyOf(shown.values()).subList(0, Math.min(6, shown.size())), List.copyOf(orders.values()),
                    List.copyOf(actions), List.copyOf(steps), support);
        }

        // the cards under the answer: the products of the latest round that found any
        void show(List<ProductResponse> products) {
            if (products.isEmpty()) return;
            if (productsRound != round) {
                shown.clear();
                productsRound = round;
            }
            products.forEach(p -> shown.putIfAbsent(p.getId(), p));
        }

        String execute(Agent.Call call) {
            steps.add(STEP_NAMES.getOrDefault(call.name(), call.name()));
            Map<String, Object> a = call.args() == null ? Map.of() : call.args();
            try {
                Object out = switch (call.name()) {
                    case "search_products" -> searchProducts(a);
                    case "get_product" -> getProduct(longArg(a, "product_id"));
                    case "recommend_for_me" -> recommend();
                    case "similar_products" -> similar(longArg(a, "product_id"));
                    case "my_orders" -> myOrders(str(a, "status"));
                    case "my_vouchers" -> myVouchers();
                    case "add_to_cart" -> addToCart(longArg(a, "product_id"), optLong(a, "variant_id"), optLong(a, "quantity"));
                    case "propose_cancel_order" -> proposeCancel(str(a, "order_code"), str(a, "reason"));
                    case "handoff_to_human" -> {
                        support = true;
                        yield Map.of("ok", true, "note", "Các nút Chat với Shop / Gửi yêu cầu hỗ trợ đã hiện bên dưới câu trả lời");
                    }
                    default -> Map.of("error", "Không có công cụ " + call.name());
                };
                return json.writeValueAsString(out);
            } catch (ApiException | IllegalArgumentException e) {
                return "{\"error\":" + quote(e.getMessage()) + "}";
            } catch (Exception e) {
                log.warn("Shopping agent tool {} failed", call.name(), e);
                return "{\"error\":\"Lỗi hệ thống, không thực hiện được\"}";
            }
        }

        Object searchProducts(Map<String, Object> a) {
            String query = str(a, "query");
            if (query == null || query.isBlank()) throw new IllegalArgumentException("Thiếu từ khóa tìm kiếm");
            BigDecimal min = decimal(a, "min_price"), max = decimal(a, "max_price");
            String sort = Objects.requireNonNullElse(str(a, "sort"), "relevance");
            return readTx.execute(st -> {
                List<Product> found = new ArrayList<>(search.search(query, min, max, 12));
                Comparator<Product> order = switch (sort) {
                    case "price_asc" -> Comparator.comparing(Product::getEffectivePrice);
                    case "price_desc" -> Comparator.comparing(Product::getEffectivePrice).reversed();
                    case "best_selling" -> Comparator.comparing((Product p) -> p.getSoldCount() == null ? 0 : p.getSoldCount()).reversed();
                    case "rating" -> Comparator.comparing((Product p) -> p.getRatingAvg() == null ? 0 : p.getRatingAvg()).reversed();
                    default -> null;
                };
                if (order != null) found.sort(order);
                List<Product> top = found.stream().limit(8).toList();
                show(top.stream().map(ProductResponse::from).toList());
                return Map.of("count", top.size(), "products", top.stream().map(ShoppingAgent::brief).toList());
            });
        }

        Object getProduct(long id) {
            return readTx.execute(st -> {
                Product p = productRepository.findById(id).filter(x -> x.isActive() && x.getShop().isActive())
                        .orElseThrow(() -> new IllegalArgumentException("Không có sản phẩm id " + id + " (hoặc đã ngừng bán)"));
                show(List.of(ProductResponse.from(p)));
                Map<String, Object> m = brief(p);
                m.put("description", cut(p.getDescription(), 600));
                m.put("returnDays", p.getReturnDays());
                m.put("category", p.getCategory() != null ? p.getCategory().getName() : null);
                m.put("variants", p.getVariants().stream().limit(30).map(v -> Map.of(
                        "variant_id", v.getId(), "name", Objects.requireNonNullElse(v.getName(), ""),
                        "price", p.unitPriceFor(v), "stock", v.getStock() == null ? 0 : v.getStock())).toList());
                return m;
            });
        }

        Object recommend() {
            List<RecommendationService.Pick> picks = recommendationService.picks(userId, 6);
            show(picks.stream().map(RecommendationService.Pick::product).toList());
            return Map.of("source", picks.stream().anyMatch(RecommendationService.Pick::learned) ? "mô hình gợi ý đã huấn luyện" : "sản phẩm bán chạy / cùng danh mục",
                    "products", picks.stream().map(x -> {
                        Map<String, Object> m = brief(x.product());
                        if (x.because() != null) m.put("because", "vì khách đã quan tâm \"" + x.because() + "\"");
                        return m;
                    }).toList());
        }

        Object similar(long id) {
            return readTx.execute(st -> {
                Product p = productRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Không có sản phẩm id " + id));
                List<ProductResponse> list = recommendationService.similar(p).stream().limit(6).toList();
                show(list);
                return Map.of("products", list.stream().map(ShoppingAgent::brief).toList());
            });
        }

        Object myOrders(String status) {
            return readTx.execute(st -> {
                List<Order> list = orderRepository.findByBuyerIdOrderByCreatedAtDesc(userId).stream()
                        .filter(o -> status == null || "ALL".equals(status) || o.getStatus().name().equals(status))
                        .limit(10).toList();
                list.stream().limit(5).forEach(o -> orders.putIfAbsent(o.getId(),
                        new ChatbotResponse.OrderRef(o.getId(), o.getCode(), o.getStatus().name(), o.getShop().getName())));
                return Map.of("count", list.size(), "orders", list.stream().map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", o.getCode());
                    m.put("shop", o.getShop().getName());
                    m.put("items", o.getItems().stream().map(i -> i.getProductNameSnapshot()
                            + (i.getVariantNameSnapshot() != null ? " (" + i.getVariantNameSnapshot() + ")" : "") + " x" + i.getQuantity()).toList());
                    m.put("status", ChatbotService.statusName(o.getStatus()));
                    m.put("total", o.getTotal());
                    m.put("orderedOn", o.getCreatedAt().format(DAY));
                    if (o.getTrackingNumber() != null) m.put("trackingNumber", o.getTrackingNumber());
                    if (o.getStatus() != OrderStatus.DELIVERED && o.getEstimatedDeliveryTo() != null) m.put("expectedBy", o.getEstimatedDeliveryTo().format(DAY));
                    m.put("cancel", cancelMode(o) == null ? "không hủy được" : "CANCEL".equals(cancelMode(o)) ? "hủy ngay được" : "gửi yêu cầu hủy cho shop được");
                    return m;
                }).toList());
            });
        }

        @SuppressWarnings("unchecked")
        Object myVouchers() {
            Map<String, Object> w = walletService.wallet();
            List<Map<String, Object>> usable = ((List<Map<String, Object>>) w.get("items")).stream()
                    .filter(m -> "USABLE".equals(m.get("state"))).limit(10)
                    .map(m -> voucher((VoucherResponse) m.get("voucher"))).toList();
            List<Map<String, Object>> collectable = ((List<VoucherResponse>) w.get("collectable")).stream().limit(5).map(ShoppingAgent::voucher).toList();
            return Map.of("usable", usable, "canSave", collectable,
                    "howToUse", "Chọn voucher ở bước Thanh toán; voucher có thể lưu nằm ở trang Kho Voucher");
        }

        Object addToCart(long productId, Long variantId, Long quantity) {
            AddToCartRequest req = new AddToCartRequest();
            req.setProductId(productId);
            req.setVariantId(variantId);
            req.setQuantity(quantity == null || quantity < 1 ? 1 : (int) Math.min(quantity, 99));
            var item = cartService.addItem(req);
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("type", "CART_ADDED");
            action.put("productId", productId);
            action.put("name", item.getProductName());
            action.put("variant", item.getVariantName());
            action.put("quantity", req.getQuantity());
            actions.add(action);
            return Map.of("ok", true, "product", item.getProductName(), "quantityInCart", item.getQuantity());
        }

        Object proposeCancel(String code, String reason) {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("Thiếu mã đơn hàng");
            return readTx.execute(st -> {
                Order o = orderRepository.findByCode(code.trim())
                        .filter(x -> x.getBuyer().getId().equals(userId))
                        .orElseThrow(() -> new IllegalArgumentException("Khách không có đơn hàng mã " + code));
                String mode = cancelMode(o);
                if (mode == null) {
                    return Map.of("ok", false, "reason", "Đơn đang ở trạng thái " + ChatbotService.statusName(o.getStatus())
                            + (o.getCancelRequestedAt() != null ? ", đã gửi yêu cầu hủy và đang chờ shop" : "") + " nên không hủy được");
                }
                orders.putIfAbsent(o.getId(), new ChatbotResponse.OrderRef(o.getId(), o.getCode(), o.getStatus().name(), o.getShop().getName()));
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("type", "CANCEL_ORDER");
                action.put("orderId", o.getId());
                action.put("code", o.getCode());
                action.put("mode", mode);
                action.put("reason", reason == null || reason.isBlank() ? "Người mua hủy đơn" : cut(reason, 200));
                actions.removeIf(x -> "CANCEL_ORDER".equals(x.get("type")) && o.getId().equals(x.get("orderId")));
                actions.add(action);
                return Map.of("ok", true, "mode", "CANCEL".equals(mode) ? "hủy ngay khi khách bấm xác nhận"
                                : "đơn shop đã xác nhận: khi khách bấm xác nhận sẽ gửi yêu cầu hủy, shop đồng ý thì mới hủy",
                        "note", "Nút xác nhận đã hiện dưới câu trả lời. Đơn CHƯA bị hủy.");
            });
        }
    }

    /** CANCEL (right away), REQUEST (ask the seller) or null (too late). Same rules as OrderService.cancelOrder. */
    private static String cancelMode(Order o) {
        if (o.getStatus() == OrderStatus.AWAITING_PAYMENT || o.getStatus() == OrderStatus.PENDING) return "CANCEL";
        if (o.getStatus() == OrderStatus.CONFIRMED && o.getCancelRequestedAt() == null && o.getCancelRequestRejectedReason() == null) return "REQUEST";
        return null;
    }

    // ==================== what the model sees ====================

    static Map<String, Object> brief(Product p) {
        return brief(ProductResponse.from(p));
    }

    static Map<String, Object> brief(ProductResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product_id", p.getId());
        m.put("name", p.getName());
        m.put("price", p.getPrice());
        if (p.getOriginalPrice() != null) m.put("originalPrice", p.getOriginalPrice());
        if (p.isFlashSale()) m.put("flashSale", true);
        m.put("sold", p.getSoldCount());
        if (p.getRatingAvg() != null && p.getRatingCount() != null && p.getRatingCount() > 0) {
            m.put("rating", Math.round(p.getRatingAvg() * 10) / 10.0);
            m.put("ratings", p.getRatingCount());
        }
        if (p.getStock() != null && p.getStock() <= 0) m.put("outOfStock", true);
        if (p.isFreeShipping()) m.put("freeShipping", true);
        m.put("shop", p.getShopName());
        if (p.getProvince() != null) m.put("shipsFrom", p.getProvince());
        return m;
    }

    static Map<String, Object> voucher(VoucherResponse v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", v.getCode());
        m.put("title", v.getTitle());
        m.put("for", v.getShopName() != null ? "shop " + v.getShopName() : "toàn sàn");
        if (v.getMinOrder() != null && v.getMinOrder().signum() > 0) m.put("minOrder", v.getMinOrder());
        if (v.getEndAt() != null) m.put("until", v.getEndAt().format(DAY));
        return m;
    }

    // ==================== arguments ====================

    private static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v == null ? null : v.toString();
    }

    private static Long optLong(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return (long) Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số");
        }
    }

    private static long longArg(Map<String, Object> a, String key) {
        Long v = optLong(a, key);
        if (v == null) throw new IllegalArgumentException("Thiếu " + key);
        return v;
    }

    private static BigDecimal decimal(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null || v.toString().isBlank()) return null;
        try {
            BigDecimal d = new BigDecimal(v.toString().trim());
            return d.signum() > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private String quote(String s) {
        try {
            return json.writeValueAsString(s == null ? "" : s);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
