package com.marketplace.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.AdminPermission;
import com.marketplace.backend.entity.AiUsageLog;
import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.AiUsageLogRepository;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.SecretCipher;
import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.service.ai.AIProvider;
import com.marketplace.backend.service.ai.AiSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Quan ly API key AI, theo doi chi phi LLM".
 *  - Configuration: provider, model, API keys (encrypted in the database; the environment variables are the
 *    fallback), a switch to turn every AI feature off, a monthly budget with an alert threshold and a hard stop,
 *    and the price per 1M tokens of each model.
 *  - Every call through AIService is logged (feature, model, tokens, estimated cost, latency, outcome).
 *  - Reports: totals, per day / feature / model, month to date against the budget with a projection.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AiOpsService {

    static final String ENABLED = "ai.enabled";
    static final String PROVIDER = "ai.provider";
    static final String OPENAI_KEY = "ai.openai.key";
    static final String OPENAI_URL = "ai.openai.url";
    static final String OPENAI_MODEL = "ai.openai.model";
    static final String HF_URL = "ai.hf.url";
    static final String HF_TOKEN = "ai.hf.token";
    static final String CLAUDE_KEY = "ai.claude.key";
    static final String CLAUDE_URL = "ai.claude.url";
    static final String CLAUDE_MODEL = "ai.claude.model";
    static final String BUDGET = "ai.budget.monthlyUsd";
    static final String ALERT_PERCENT = "ai.budget.alertPercent";
    static final String HARD_STOP = "ai.budget.hardStop";
    static final String USD_RATE = "ai.usdRate";
    static final String PRICING = "ai.pricing";
    static final String ALERTED = "ai.budget.alerted";      // "2026-10:80" - last alert sent, so it is sent once

    /** USD per 1M tokens [input, output] for known models; editable on the admin page. */
    static final Map<String, BigDecimal[]> DEFAULT_PRICING = new LinkedHashMap<>();

    static {
        DEFAULT_PRICING.put("gpt-4o-mini", new BigDecimal[]{new BigDecimal("0.15"), new BigDecimal("0.60")});
        DEFAULT_PRICING.put("gpt-4o", new BigDecimal[]{new BigDecimal("2.50"), new BigDecimal("10.00")});
        DEFAULT_PRICING.put("gpt-4.1-mini", new BigDecimal[]{new BigDecimal("0.40"), new BigDecimal("1.60")});
        DEFAULT_PRICING.put("gpt-4.1", new BigDecimal[]{new BigDecimal("2.00"), new BigDecimal("8.00")});
        // Claude: fill in the prices of the models you use on the admin page (USD per 1M tokens)
        DEFAULT_PRICING.put("claude-haiku-4-5-20251001", new BigDecimal[]{new BigDecimal("1.00"), new BigDecimal("5.00")});
        DEFAULT_PRICING.put("claude-sonnet-5-5", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        DEFAULT_PRICING.put("claude-opus-5-5", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        DEFAULT_PRICING.put("vistral-7b-shoppi", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
    }

    /** caller class -> feature key, and the labels shown on the dashboard */
    static final Map<String, String> CALLERS = Map.of(
            "ChatbotService", "CHATBOT", "SellerAIService", "PRODUCT_WRITER", "SellerAIController", "PRODUCT_WRITER",
            "ReviewInsightService", "REVIEW_INSIGHT", "SalesAssistantService", "SALES_ASSISTANT", "PromoAdvisorService", "PROMO_ADVISOR",
            "ReplySuggestService", "REPLY_SUGGEST", "AIService", "CONNECTION_TEST",
            "ReviewAiService", "REVIEW_HUB", "ReviewHubService", "REVIEW_HUB");
    static final Map<String, String> FEATURES = new LinkedHashMap<>();

    static {
        FEATURES.put("CHATBOT", "Chatbot hỗ trợ người mua");
        FEATURES.put("PRODUCT_WRITER", "Viết mô tả & gợi ý giá");
        FEATURES.put("REVIEW_INSIGHT", "Phân tích đánh giá");
        FEATURES.put("SALES_ASSISTANT", "Trợ lý doanh thu");
        FEATURES.put("PROMO_ADVISOR", "Gợi ý khuyến mãi");
        FEATURES.put("REPLY_SUGGEST", "Gợi ý trả lời khách");
        FEATURES.put("REVIEW_HUB", "Quản lý đánh giá (phân loại, soạn trả lời)");
        FEATURES.put("CONNECTION_TEST", "Kiểm tra kết nối");
        FEATURES.put("OTHER", "Khác");
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformSettingRepository settings;
    private final AiUsageLogRepository usageRepository;
    private final SecretCipher cipher;
    private final AdminAuditService auditService;

    @Value("${app.ai.provider:openai}")
    private String envProvider;
    @Value("${openai.api.key:}")
    private String envOpenaiKey;
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String envOpenaiUrl;
    @Value("${openai.model:gpt-4o-mini}")
    private String envModel;
    @Value("${app.ai.huggingface.endpoint-url:}")
    private String envHfUrl;
    @Value("${app.ai.huggingface.token:}")
    private String envHfToken;
    @Value("${ANTHROPIC_API_KEY:}")
    private String envClaudeKey;
    @Value("${ANTHROPIC_API_URL:https://api.anthropic.com/v1/messages}")
    private String envClaudeUrl;
    @Value("${ANTHROPIC_MODEL:claude-sonnet-5-5}")
    private String envClaudeModel;

    // ==================== configuration in force ====================

    @Transactional(readOnly = true)
    public AiSettings settings() {
        return new AiSettings(!"false".equals(get(ENABLED)), or(get(PROVIDER), envProvider),
                or(secret(OPENAI_KEY), envOpenaiKey), or(get(OPENAI_URL), envOpenaiUrl), or(get(OPENAI_MODEL), envModel),
                or(get(HF_URL), envHfUrl), or(secret(HF_TOKEN), envHfToken),
                or(secret(CLAUDE_KEY), envClaudeKey), or(get(CLAUDE_URL), envClaudeUrl), or(get(CLAUDE_MODEL), envClaudeModel));
    }

    /** Why AI calls can't run now (switched off, not configured, budget used up), or null. */
    @Transactional(readOnly = true)
    public String blockReason(AiSettings s) {
        if (!s.enabled()) return "Các tính năng AI đang được tạm tắt";
        if (!s.configured()) return "AI chưa được cấu hình";
        BigDecimal budget = budget();
        if (budget != null && !"false".equals(get(HARD_STOP)) && monthCost().compareTo(budget) >= 0) return "Đã dùng hết ngân sách AI tháng này";
        return null;
    }

    /** The feature that is calling, from the call stack (so the callers don't have to say it). */
    static String featureOfCaller() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(f -> f.getClassName())
                .filter(c -> c.startsWith("com.marketplace.backend") && !c.contains(".service.ai.") && !c.endsWith(".AiOpsService"))
                .map(c -> c.substring(c.lastIndexOf('.') + 1).replaceAll("\\$.*", ""))
                .filter(c -> !c.equals("AIService"))
                .findFirst()).map(c -> CALLERS.getOrDefault(c, "OTHER")).orElse("OTHER");
    }

    // ==================== logging each call ====================

    /** Written in its own transaction: a call made inside a read-only transaction must still be recorded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String feature, AiSettings s, AIProvider.Completion c, int latencyMs, String blocked) {
        BigDecimal cost = BigDecimal.ZERO;
        AiUsageLog.Status status = blocked != null ? AiUsageLog.Status.BLOCKED : c.error() == null ? AiUsageLog.Status.SUCCESS : AiUsageLog.Status.FAILED;
        if (c != null) {
            BigDecimal[] price = pricing().getOrDefault(s.model(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cost = price[0].multiply(BigDecimal.valueOf(c.promptTokens())).add(price[1].multiply(BigDecimal.valueOf(c.completionTokens())))
                    .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }
        usageRepository.save(AiUsageLog.builder().feature(feature).provider(s.providerKey()).model(s.model())
                .promptTokens(c == null ? 0 : c.promptTokens()).completionTokens(c == null ? 0 : c.completionTokens())
                .estimated(c != null && c.estimated()).costUsd(cost).latencyMs(c == null ? null : latencyMs).status(status)
                .error(blocked != null ? blocked : c == null || c.error() == null ? null : OpenAiProviderCut.cut(c.error(), 300))
                .userId(currentUserOrNull()).createdAt(LocalDateTime.now()).build());
        if (cost.signum() > 0) checkBudget();
    }

    // tells the admins once when the month passes the alert threshold, and once when the budget is used up
    private void checkBudget() {
        BigDecimal budget = budget();
        if (budget == null || budget.signum() == 0) return;
        BigDecimal spent = monthCost();
        int alert = alertPercent();
        String month = YearMonth.now().toString();
        String level = spent.compareTo(budget) >= 0 ? "100" : spent.multiply(BigDecimal.valueOf(100)).compareTo(budget.multiply(BigDecimal.valueOf(alert))) >= 0 ? String.valueOf(alert) : null;
        if (level == null || (month + ":" + level).equals(get(ALERTED)) || ("100".equals(level) == false && (month + ":100").equals(get(ALERTED)))) return;
        put(ALERTED, month + ":" + level);
        boolean stop = "100".equals(level) && !"false".equals(get(HARD_STOP));
        auditService.notifyAdmins(AdminPermission.SETTINGS,
                "100".equals(level) ? "Đã dùng hết ngân sách AI tháng " + month : "Chi phí AI đã đạt " + level + "% ngân sách tháng",
                "Đã dùng $" + spent.setScale(2, RoundingMode.HALF_UP) + " / $" + budget.setScale(2, RoundingMode.HALF_UP)
                        + (stop ? ". Các tính năng AI tạm dừng đến hết tháng (chuyển sang câu trả lời không dùng AI)." : "."),
                "/admin/ai");
    }

    // ==================== admin: configuration ====================

    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        AiSettings s = settings();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", s.enabled());
        m.put("provider", s.providerKey());
        m.put("claudeModel", s.claudeModel());
        m.put("claudeUrl", s.claudeUrl());
        m.put("claudeKey", keyView(CLAUDE_KEY, envClaudeKey));
        m.put("openaiModel", s.openaiModel());
        m.put("openaiUrl", s.openaiUrl());
        m.put("openaiKey", keyView(OPENAI_KEY, envOpenaiKey));
        m.put("hfUrl", s.hfUrl());
        m.put("hfToken", keyView(HF_TOKEN, envHfToken));
        m.put("budgetUsd", budget());
        m.put("alertPercent", alertPercent());
        m.put("hardStop", !"false".equals(get(HARD_STOP)));
        m.put("usdRate", usdRate());
        Map<String, Object> prices = new LinkedHashMap<>();
        pricing().forEach((model, p) -> prices.put(model, AdminUserService.map("input", p[0], "output", p[1])));
        m.put("pricing", prices);
        m.put("models", List.copyOf(DEFAULT_PRICING.keySet()));
        m.put("status", Optional.ofNullable(blockReason(s)).orElse("READY"));
        m.put("canEditKeys", isSuperAdmin());
        return m;
    }

    /**
     * body: { enabled?, provider?, openaiModel?, openaiUrl?, openaiKey?, clearOpenaiKey?, hfUrl?, hfToken?, clearHfToken?,
     *         budgetUsd?, alertPercent?, hardStop?, usdRate?, pricing?: { model: { input, output } } }
     * Keys and endpoints can only be changed by a super admin.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(Map<String, Object> body) {
        boolean keysTouched = body.containsKey("openaiKey") || body.containsKey("clearOpenaiKey") || body.containsKey("hfToken")
                || body.containsKey("clearHfToken") || body.containsKey("openaiUrl") || body.containsKey("hfUrl")
                || body.containsKey("claudeKey") || body.containsKey("clearClaudeKey") || body.containsKey("claudeUrl");
        if (keysTouched && !isSuperAdmin()) throw new ApiException("Chỉ quản trị viên cấp cao được thay đổi API key và endpoint", HttpStatus.FORBIDDEN);
        List<String> changes = new ArrayList<>();

        if (body.get("enabled") instanceof Boolean b && b != settings().enabled()) {
            put(ENABLED, String.valueOf(b));
            changes.add(b ? "Bật các tính năng AI" : "Tắt toàn bộ tính năng AI");
        }
        if (body.get("provider") instanceof String p) {
            if (!p.equals("openai") && !p.equals("huggingface") && !p.equals("claude")) throw bad("Nhà cung cấp không hợp lệ");
            if (!p.equals(settings().providerKey())) {
                put(PROVIDER, p);
                changes.add("Nhà cung cấp: " + (p.equals("openai") ? "OpenAI" : p.equals("claude") ? "Anthropic Claude" : "Mô hình tự train (Hugging Face)"));
            }
        }
        if (body.get("openaiModel") instanceof String model) {
            String v = model.trim();
            if (!v.matches("[A-Za-z0-9._:\\-]{2,60}")) throw bad("Tên model không hợp lệ");
            if (!v.equals(settings().openaiModel())) {
                put(OPENAI_MODEL, v);
                changes.add("Model: " + v);
            }
        }
        if (body.get("claudeModel") instanceof String model) {
            String v = model.trim();
            if (!v.matches("[A-Za-z0-9._:\\-]{2,60}")) throw bad("Tên model không hợp lệ");
            if (!v.equals(settings().claudeModel())) {
                put(CLAUDE_MODEL, v);
                changes.add("Model Claude: " + v);
            }
        }
        url(body, "claudeUrl", CLAUDE_URL, "Endpoint Claude", changes);
        secretField(body, "claudeKey", "clearClaudeKey", CLAUDE_KEY, "Claude API key", changes);
        url(body, "openaiUrl", OPENAI_URL, "Endpoint OpenAI", changes);
        url(body, "hfUrl", HF_URL, "Endpoint Hugging Face", changes);
        secretField(body, "openaiKey", "clearOpenaiKey", OPENAI_KEY, "OpenAI API key", changes);
        secretField(body, "hfToken", "clearHfToken", HF_TOKEN, "Hugging Face token", changes);

        if (body.containsKey("budgetUsd")) {
            Object v = body.get("budgetUsd");
            BigDecimal b = v == null || v.toString().isBlank() ? null : new BigDecimal(v.toString());
            if (b != null && (b.signum() < 0 || b.compareTo(new BigDecimal("100000")) > 0)) throw bad("Ngân sách không hợp lệ");
            if (!Objects.equals(b == null ? null : b.stripTrailingZeros(), budget() == null ? null : budget().stripTrailingZeros())) {
                put(BUDGET, b == null ? null : b.toPlainString());
                put(ALERTED, null);
                changes.add("Ngân sách tháng: " + (b == null ? "không giới hạn" : "$" + b.toPlainString()));
            }
        }
        if (body.get("alertPercent") instanceof Number n) {
            if (n.intValue() < 10 || n.intValue() > 100) throw bad("Ngưỡng cảnh báo từ 10% đến 100%");
            if (n.intValue() != alertPercent()) {
                put(ALERT_PERCENT, String.valueOf(n.intValue()));
                changes.add("Cảnh báo khi đạt " + n.intValue() + "% ngân sách");
            }
        }
        if (body.get("hardStop") instanceof Boolean b && b != !"false".equals(get(HARD_STOP))) {
            put(HARD_STOP, String.valueOf(b));
            changes.add(b ? "Tự dừng AI khi hết ngân sách" : "Không tự dừng khi hết ngân sách");
        }
        if (body.get("usdRate") instanceof Number n) {
            if (n.intValue() < 1000 || n.intValue() > 100000) throw bad("Tỷ giá không hợp lệ");
            if (n.intValue() != usdRate()) {
                put(USD_RATE, String.valueOf(n.intValue()));
                changes.add("Tỷ giá: " + n.intValue() + " ₫/USD");
            }
        }
        if (body.get("pricing") instanceof Map<?, ?> raw) {
            Map<String, BigDecimal[]> next = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                String model = e.getKey().toString().trim();
                if (!model.matches("[A-Za-z0-9._:\\-]{2,60}") || !(e.getValue() instanceof Map<?, ?> p)) throw bad("Bảng giá không hợp lệ");
                BigDecimal in = price(p.get("input")), out = price(p.get("output"));
                next.put(model, new BigDecimal[]{in, out});
            }
            if (!samePricing(next, pricing())) {
                Map<String, Map<String, String>> json = new LinkedHashMap<>();
                next.forEach((k, p) -> json.put(k, Map.of("input", p[0].toPlainString(), "output", p[1].toPlainString())));
                try {
                    put(PRICING, JSON.writeValueAsString(json));
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                changes.add("Bảng giá model (" + next.size() + " model)");
            }
        }
        if (!changes.isEmpty()) auditService.log("UPDATE_AI_CONFIG", "SETTING", null, "Cấu hình AI", String.join("; ", changes));
        return config();
    }

    private void url(Map<String, Object> body, String field, String key, String label, List<String> changes) {
        if (!(body.get(field) instanceof String v)) return;
        String u = v.trim();
        // https only, except a model served on this machine during development
        if (!u.isEmpty() && !u.matches("https://[^\\s]{4,300}|http://(localhost|127\\.0\\.0\\.1)(:\\d{2,5})?/[^\\s]{0,200}")) {
            throw bad(label + " phải là địa chỉ https://");
        }
        if (!u.equals(Objects.requireNonNullElse(get(key), ""))) {
            put(key, u.isEmpty() ? null : u);
            changes.add(label + (u.isEmpty() ? ": dùng giá trị mặc định" : ": " + u));
        }
    }

    private void secretField(Map<String, Object> body, String field, String clearField, String key, String label, List<String> changes) {
        if (Boolean.TRUE.equals(body.get(clearField))) {
            if (get(key) != null) {
                put(key, null);
                changes.add("Xóa " + label + " (dùng biến môi trường nếu có)");
            }
            return;
        }
        if (body.get(field) instanceof String v && !v.isBlank()) {
            String k = v.trim();
            if (k.length() < 8 || k.length() > 300 || k.contains(" ")) throw bad(label + " không hợp lệ");
            put(key, cipher.encrypt(k));
            changes.add("Đổi " + label + " (" + SecretCipher.mask(k) + ")");   // never the key itself
        }
    }

    private Map<String, Object> keyView(String key, String env) {
        String stored = get(key);
        String plain = secret(key);
        if (stored != null && plain == null) return AdminUserService.map("source", "UNREADABLE", "masked", null);
        if (plain != null) return AdminUserService.map("source", "ADMIN", "masked", SecretCipher.mask(plain));
        if (env != null && !env.isBlank()) return AdminUserService.map("source", "ENV", "masked", SecretCipher.mask(env));
        return AdminUserService.map("source", "NONE", "masked", null);
    }

    // ==================== admin: usage & cost ====================

    @Transactional(readOnly = true)
    public Map<String, Object> usage(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) throw bad("Ngày bắt đầu phải trước ngày kết thúc");
        if (f.plusDays(366).isBefore(t)) throw bad("Mỗi lần chỉ xem được tối đa 366 ngày");
        List<AiUsageLog> rows = usageRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(f.atStartOfDay(), t.plusDays(1).atStartOfDay());
        int rate = usdRate();

        Map<LocalDate, List<AiUsageLog>> byDay = rows.stream().collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            List<AiUsageLog> in = byDay.getOrDefault(d, List.of());
            series.add(AdminUserService.map("date", d, "label", String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()),
                    "calls", in.size(), "failed", in.stream().filter(r -> r.getStatus() != AiUsageLog.Status.SUCCESS).count(),
                    "tokens", in.stream().mapToLong(r -> r.getPromptTokens() + r.getCompletionTokens()).sum(), "costUsd", cost(in)));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", f);
        m.put("to", t);
        m.put("usdRate", rate);
        m.put("summary", summary(rows, rate));
        m.put("series", series);
        m.put("byFeature", group(rows, AiUsageLog::getFeature, rate, k -> FEATURES.getOrDefault(k, k)));
        m.put("byModel", group(rows, r -> r.getModel() == null ? "?" : r.getModel(), rate, k -> k));

        // this month against the budget, and where it is heading at the current pace
        YearMonth ym = YearMonth.now();
        BigDecimal mtd = monthCost();
        int day = LocalDate.now().getDayOfMonth();
        BigDecimal projected = mtd.multiply(BigDecimal.valueOf(ym.lengthOfMonth())).divide(BigDecimal.valueOf(day), 6, RoundingMode.HALF_UP);
        BigDecimal budget = budget();
        m.put("month", AdminUserService.map("month", ym.toString(), "costUsd", mtd, "costVnd", vnd(mtd, rate), "budgetUsd", budget,
                "usedPercent", budget == null || budget.signum() == 0 ? null : mtd.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP),
                "projectedUsd", projected, "projectedVnd", vnd(projected, rate),
                "blocked", blockReason(settings()) != null && budget != null && mtd.compareTo(budget) >= 0));
        return m;
    }

    /** status: SUCCESS | FAILED | BLOCKED | ALL; feature key or empty */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> calls(String status, String feature, int page, int size) {
        AiUsageLog.Status st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = AiUsageLog.Status.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        int rate = usdRate();
        return PageResponse.of(usageRepository.search(st, feature == null ? "" : feature.trim().toUpperCase(Locale.ROOT),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())),
                r -> AdminUserService.map("id", r.getId(), "createdAt", r.getCreatedAt(), "feature", r.getFeature(),
                        "featureLabel", FEATURES.getOrDefault(r.getFeature(), r.getFeature()), "provider", r.getProvider(), "model", r.getModel(),
                        "promptTokens", r.getPromptTokens(), "completionTokens", r.getCompletionTokens(), "estimated", r.isEstimated(),
                        "costUsd", r.getCostUsd(), "costVnd", vnd(r.getCostUsd(), rate), "latencyMs", r.getLatencyMs(),
                        "status", r.getStatus().name(), "error", r.getError(), "userId", r.getUserId()));
    }

    private Map<String, Object> summary(List<AiUsageLog> rows, int rate) {
        List<Integer> lat = rows.stream().map(AiUsageLog::getLatencyMs).filter(Objects::nonNull).sorted().toList();
        BigDecimal c = cost(rows);
        long ok = rows.stream().filter(r -> r.getStatus() == AiUsageLog.Status.SUCCESS).count();
        return AdminUserService.map("calls", rows.size(), "success", ok,
                "failed", rows.stream().filter(r -> r.getStatus() == AiUsageLog.Status.FAILED).count(),
                "blocked", rows.stream().filter(r -> r.getStatus() == AiUsageLog.Status.BLOCKED).count(),
                "successRate", rows.isEmpty() ? null : Math.round(ok * 1000.0 / rows.size()) / 10.0,
                "promptTokens", rows.stream().mapToLong(AiUsageLog::getPromptTokens).sum(),
                "completionTokens", rows.stream().mapToLong(AiUsageLog::getCompletionTokens).sum(),
                "costUsd", c, "costVnd", vnd(c, rate),
                "avgLatencyMs", lat.isEmpty() ? null : Math.round(lat.stream().mapToInt(Integer::intValue).average().orElse(0)),
                "p95LatencyMs", lat.isEmpty() ? null : lat.get(Math.min(lat.size() - 1, (int) Math.ceil(lat.size() * 0.95) - 1)),
                "costPerCallVnd", ok == 0 ? BigDecimal.ZERO : vnd(c, rate).divide(BigDecimal.valueOf(ok), 0, RoundingMode.HALF_UP));
    }

    private List<Map<String, Object>> group(List<AiUsageLog> rows, java.util.function.Function<AiUsageLog, String> key, int rate,
                                            java.util.function.Function<String, String> label) {
        BigDecimal total = cost(rows);
        return rows.stream().collect(Collectors.groupingBy(key)).entrySet().stream().map(e -> {
            List<AiUsageLog> in = e.getValue();
            BigDecimal c = cost(in);
            return AdminUserService.map("key", e.getKey(), "label", label.apply(e.getKey()), "calls", in.size(),
                    "failed", in.stream().filter(r -> r.getStatus() != AiUsageLog.Status.SUCCESS).count(),
                    "tokens", in.stream().mapToLong(r -> r.getPromptTokens() + r.getCompletionTokens()).sum(),
                    "costUsd", c, "costVnd", vnd(c, rate),
                    "share", total.signum() == 0 ? 0 : c.multiply(BigDecimal.valueOf(1000)).divide(total, 0, RoundingMode.HALF_UP).doubleValue() / 10);
        }).sorted(Comparator.comparing((Map<String, Object> x) -> (BigDecimal) x.get("costUsd")).reversed()
                .thenComparing(x -> -((Number) x.get("calls")).longValue())).toList();
    }

    // ==================== helpers ====================

    BigDecimal monthCost() {
        YearMonth ym = YearMonth.now();
        return usageRepository.costBetween(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
    }

    private static BigDecimal cost(List<AiUsageLog> rows) {
        return rows.stream().map(AiUsageLog::getCostUsd).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal vnd(BigDecimal usd, int rate) {
        return usd == null ? BigDecimal.ZERO : usd.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal budget() {
        String v = get(BUDGET);
        return v == null ? null : new BigDecimal(v);
    }

    private int alertPercent() {
        String v = get(ALERT_PERCENT);
        return v == null ? 80 : Integer.parseInt(v);
    }

    private int usdRate() {
        String v = get(USD_RATE);
        return v == null ? 25_000 : Integer.parseInt(v);
    }

    Map<String, BigDecimal[]> pricing() {
        Map<String, BigDecimal[]> m = new LinkedHashMap<>(DEFAULT_PRICING);
        String v = get(PRICING);
        if (v != null) {
            try {
                Map<String, Map<String, String>> raw = JSON.readValue(v, new TypeReference<>() { });
                m.clear();
                raw.forEach((k, p) -> m.put(k, new BigDecimal[]{new BigDecimal(p.get("input")), new BigDecimal(p.get("output"))}));
            } catch (Exception ignored) {
                // a broken value falls back to the defaults
            }
        }
        return m;
    }

    private static boolean samePricing(Map<String, BigDecimal[]> a, Map<String, BigDecimal[]> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        return a.entrySet().stream().allMatch(e -> e.getValue()[0].compareTo(b.get(e.getKey())[0]) == 0 && e.getValue()[1].compareTo(b.get(e.getKey())[1]) == 0);
    }

    private static BigDecimal price(Object o) {
        try {
            BigDecimal v = new BigDecimal(String.valueOf(o));
            if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(1000)) > 0) throw bad("Giá mỗi 1 triệu token không hợp lệ");
            return v.stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw bad("Giá mỗi 1 triệu token không hợp lệ");
        }
    }

    private String get(String key) {
        return settings.findById(key).map(PlatformSetting::getValue).orElse(null);
    }

    private String secret(String key) {
        return cipher.decrypt(get(key));
    }

    private void put(String key, String value) {
        if (value == null) {
            settings.findById(key).ifPresent(settings::delete);
            return;
        }
        PlatformSetting s = settings.findById(key).orElseGet(PlatformSetting::new);
        s.setKey(key);
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedBy(currentUserOrNull());
        settings.save(s);
    }

    private static boolean isSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p && p.isSuperAdmin();
    }

    private static Long currentUserOrNull() {
        try {
            return CurrentUser.id();
        } catch (Exception e) {
            return null;   // scheduled jobs / guests
        }
    }

    private static String or(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

    // small text cut shared with the providers
    static final class OpenAiProviderCut {
        static String cut(String s, int max) {
            return s.length() > max ? s.substring(0, max - 1) + "…" : s;
        }
    }
}
