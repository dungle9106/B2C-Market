package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * "Su kien Flash Sale toan san": the marketing team opens time slots; sellers register products with a flash
 * price (at least minDiscountPercent off) before the deadline; the team approves or rejects each one. An
 * approved product gets the product's flash sale fields for the slot, so the price, the quota, the per-buyer
 * limit, search, product pages and checkout all work as for a shop's own flash sale.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PlatformFlashSaleService {

    private static final Set<FlashSaleRegistration.Status> LIVE = EnumSet.of(FlashSaleRegistration.Status.PENDING, FlashSaleRegistration.Status.APPROVED);
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final FlashSaleEventRepository eventRepository;
    private final FlashSaleRegistrationRepository registrationRepository;
    private final PromoCampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    // ==================== admin: slots ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] r : registrationRepository.countsByEvent()) {
            counts.computeIfAbsent((Long) r[0], k -> new HashMap<>()).put(((FlashSaleRegistration.Status) r[1]).name(), ((Number) r[2]).longValue());
        }
        return eventRepository.findAllByOrderByStartAtDesc().stream().map(e -> {
            Map<String, Object> m = view(e, now);
            Map<String, Long> c = counts.getOrDefault(e.getId(), Map.of());
            m.put("pending", c.getOrDefault("PENDING", 0L));
            m.put("approved", c.getOrDefault("APPROVED", 0L));
            m.put("rejected", c.getOrDefault("REJECTED", 0L));
            return m;
        }).toList();
    }

    /** body: { name, startAt, endAt, registrationDeadline, minDiscountPercent?, maxProductsPerShop?, campaignId? } */
    public Map<String, Object> create(Map<String, Object> body) {
        FlashSaleEvent e = FlashSaleEvent.builder().createdBy(CurrentUser.id()).build();
        fill(e, body, true);
        eventRepository.save(e);
        // every seller is invited to register
        notificationService.notifyMany(userRepository.findEnabledIdsByRole(Role.SELLER), NotificationType.PROMOTION,
                "Đăng ký Flash Sale Shoppi: " + e.getName(),
                "Khung giờ " + slot(e) + ", giảm tối thiểu " + e.getMinDiscountPercent() + "%. Hạn đăng ký "
                        + e.getRegistrationDeadline().format(SLOT) + ".", "/seller/flash-events");
        auditService.log("CREATE_FLASH_EVENT", "FLASH_EVENT", e.getId(), e.getName(), slot(e));
        return view(e, LocalDateTime.now());
    }

    /** Before registration closes everything can change; afterwards only the name and the programme. */
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        FlashSaleEvent e = find(id);
        String st = e.status(LocalDateTime.now());
        if (st.equals("CANCELLED") || st.equals("ENDED")) throw bad("Khung giờ đã kết thúc hoặc bị hủy");
        fill(e, body, st.equals("REGISTERING"));
        auditService.log("UPDATE_FLASH_EVENT", "FLASH_EVENT", e.getId(), e.getName(), slot(e));
        return view(e, LocalDateTime.now());
    }

    /** Cancels a slot: upcoming flash prices are removed, a running one ends now; sellers are told. */
    public Map<String, Object> cancel(Long id, String reason) {
        FlashSaleEvent e = find(id);
        LocalDateTime now = LocalDateTime.now();
        String st = e.status(now);
        if (st.equals("CANCELLED") || st.equals("ENDED")) throw bad("Khung giờ đã kết thúc hoặc bị hủy");
        if (reason == null || reason.trim().length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        e.setCancelled(true);
        Set<Long> sellers = new HashSet<>();
        for (FlashSaleRegistration r : registrationRepository.forEvent(id, null)) {
            if (r.getStatus() == FlashSaleRegistration.Status.APPROVED) unapply(r.getProduct(), e, now);
            if (LIVE.contains(r.getStatus())) sellers.add(r.getShop().getSeller().getId());
        }
        notificationService.notifyMany(sellers, NotificationType.PROMOTION, "Flash Sale Shoppi " + e.getName() + " đã bị hủy",
                reason.trim(), "/seller/flash-events");
        auditService.log("CANCEL_FLASH_EVENT", "FLASH_EVENT", e.getId(), e.getName(), reason.trim());
        return view(e, now);
    }

    /** One slot with every registration and, once it ran, the results. */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id, String status) {
        FlashSaleEvent e = find(id);
        LocalDateTime now = LocalDateTime.now();
        FlashSaleRegistration.Status st = status == null || status.isBlank() || status.equalsIgnoreCase("ALL") ? null : parse(status);
        Map<String, Object> m = view(e, now);
        List<FlashSaleRegistration> all = registrationRepository.forEvent(id, null);
        m.put("registrations", all.stream().filter(r -> st == null || r.getStatus() == st).map(r -> registrationView(r, now)).toList());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FlashSaleRegistration.Status s : FlashSaleRegistration.Status.values()) counts.put(s.name(), 0L);
        all.forEach(r -> counts.merge(r.getStatus().name(), 1L, Long::sum));
        m.put("counts", counts);
        m.put("shops", all.stream().filter(r -> LIVE.contains(r.getStatus())).map(r -> r.getShop().getId()).distinct().count());
        List<Long> approved = all.stream().filter(r -> r.getStatus() == FlashSaleRegistration.Status.APPROVED).map(r -> r.getProduct().getId()).toList();
        Object[] res = approved.isEmpty() ? new Object[]{0L, BigDecimal.ZERO, 0L}
                : orderItemRepository.flashResults(approved, e.getStartAt(), e.getEndAt()).get(0);
        m.put("results", AdminUserService.map("units", ((Number) res[0]).longValue(), "revenue", new BigDecimal(res[1].toString()),
                "orders", ((Number) res[2]).longValue()));
        return m;
    }

    // ==================== admin: review ====================

    public Map<String, Object> approve(Long registrationId) {
        FlashSaleRegistration r = findRegistration(registrationId);
        approveOne(r, LocalDateTime.now());
        notificationService.notify(r.getShop().getSeller().getId(), NotificationType.PROMOTION,
                "Sản phẩm được duyệt tham gia Flash Sale Shoppi",
                "\"" + r.getProduct().getName() + "\" sẽ bán giá " + NotificationService.vnd(r.getFlashPrice()) + " trong khung giờ " + slot(r.getEvent()) + ".",
                "/seller/flash-events");
        auditService.log("APPROVE_FLASH_ITEM", "FLASH_EVENT", r.getEvent().getId(), r.getEvent().getName(), r.getProduct().getName());
        return registrationView(r, LocalDateTime.now());
    }

    /** Approves every pending registration it can; the others are listed with the reason. */
    public Map<String, Object> approveAll(Long eventId) {
        FlashSaleEvent e = find(eventId);
        LocalDateTime now = LocalDateTime.now();
        int approved = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        Map<Long, Integer> perSeller = new HashMap<>();
        for (FlashSaleRegistration r : registrationRepository.forEvent(eventId, FlashSaleRegistration.Status.PENDING)) {
            try {
                approveOne(r, now);
                approved++;
                perSeller.merge(r.getShop().getSeller().getId(), 1, Integer::sum);
            } catch (ApiException ex) {
                skipped.add(AdminUserService.map("id", r.getId(), "product", r.getProduct().getName(), "reason", ex.getMessage()));
            }
        }
        perSeller.forEach((sellerId, n) -> notificationService.notify(sellerId, NotificationType.PROMOTION,
                "Sản phẩm được duyệt tham gia Flash Sale Shoppi", n + " sản phẩm của shop sẽ lên Flash Sale " + e.getName() + " (" + slot(e) + ").",
                "/seller/flash-events"));
        if (approved > 0) auditService.log("APPROVE_FLASH_ITEM", "FLASH_EVENT", e.getId(), e.getName(), "Duyệt hàng loạt " + approved + " sản phẩm");
        return AdminUserService.map("approved", approved, "skipped", skipped);
    }

    public Map<String, Object> reject(Long registrationId, String reason) {
        FlashSaleRegistration r = findRegistration(registrationId);
        LocalDateTime now = LocalDateTime.now();
        if (!LIVE.contains(r.getStatus())) throw bad("Đăng ký này không còn chờ duyệt");
        if (reason == null || reason.trim().length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        String st = r.getEvent().status(now);
        if (st.equals("ENDED") || st.equals("CANCELLED")) throw bad("Khung giờ đã kết thúc");
        boolean wasApproved = r.getStatus() == FlashSaleRegistration.Status.APPROVED;
        r.setStatus(FlashSaleRegistration.Status.REJECTED);
        r.setRejectReason(reason.trim());
        r.setReviewedAt(now);
        if (wasApproved) unapply(r.getProduct(), r.getEvent(), now);
        notificationService.notify(r.getShop().getSeller().getId(), NotificationType.PROMOTION,
                "Sản phẩm không được duyệt tham gia Flash Sale Shoppi", "\"" + r.getProduct().getName() + "\": " + reason.trim(), "/seller/flash-events");
        auditService.log("REJECT_FLASH_ITEM", "FLASH_EVENT", r.getEvent().getId(), r.getEvent().getName(), r.getProduct().getName() + " – " + reason.trim());
        return registrationView(r, now);
    }

    private void approveOne(FlashSaleRegistration r, LocalDateTime now) {
        if (r.getStatus() != FlashSaleRegistration.Status.PENDING) throw bad("Đăng ký này không còn chờ duyệt");
        FlashSaleEvent e = r.getEvent();
        String st = e.status(now);
        if (st.equals("ENDED") || st.equals("CANCELLED")) throw bad("Khung giờ đã kết thúc hoặc bị hủy");
        Product p = r.getProduct();
        String problem = productProblem(p, r.getFlashPrice(), r.getFlashStock(), e);
        if (problem == null) problem = shopFlashProblem(p, now);
        if (problem != null) throw bad(problem);
        r.setStatus(FlashSaleRegistration.Status.APPROVED);
        r.setRejectReason(null);
        r.setReviewedAt(now);
        registrationRepository.flush();
        applyNext(p, now);
    }

    /**
     * A product holds one flash sale window at a time, but may be approved into several Shoppi slots: it carries
     * the earliest approved slot that is not over; the next one is put on when it is due (activateDueSlots).
     * A running slot is never replaced.
     */
    void applyNext(Product p, LocalDateTime now) {
        String current = FlashSaleService.status(p, now);
        boolean runningSlot = p.getFlashSaleEventId() != null && (current.equals("RUNNING") || current.equals("SOLD_OUT"));
        if (runningSlot && registrationRepository.findByEventIdAndProductId(p.getFlashSaleEventId(), p.getId())
                .map(x -> x.getStatus() == FlashSaleRegistration.Status.APPROVED && !x.getEvent().isCancelled()).orElse(false)) {
            return;
        }
        // the shop's own flash sale is left alone
        if (p.getFlashSaleEventId() == null && !current.equals("NONE") && !current.equals("ENDED")) return;
        FlashSaleRegistration next = registrationRepository.approvedAhead(p.getId(), now, FlashSaleRegistration.Status.APPROVED)
                .stream().findFirst().orElse(null);
        if (next == null) {
            // nothing approved ahead: drop an upcoming slot price that is no longer valid
            if (p.getFlashSaleEventId() != null && current.equals("UPCOMING")) clear(p);
            return;
        }
        if (Objects.equals(p.getFlashSaleEventId(), next.getEvent().getId()) && !current.equals("ENDED")) return;
        p.setFlashSalePrice(next.getFlashPrice());
        p.setFlashSaleStart(next.getEvent().getStartAt());
        p.setFlashSaleEnd(next.getEvent().getEndAt());
        p.setFlashSaleStock(next.getFlashStock());
        p.setFlashSaleSold(0);
        p.setFlashSaleMaxPerBuyer(next.getMaxPerBuyer());
        p.setFlashSaleEventId(next.getEvent().getId());
    }

    /** Puts the next approved slot on products whose slot starts within two minutes (or already runs). */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void activateDueSlots() {
        LocalDateTime now = LocalDateTime.now();
        for (FlashSaleRegistration r : registrationRepository.due(now, now.plusMinutes(2), FlashSaleRegistration.Status.APPROVED)) {
            if (!Objects.equals(r.getProduct().getFlashSaleEventId(), r.getEvent().getId())) applyNext(r.getProduct(), now);
        }
    }

    // takes the slot's flash price off the product (ends it now if running), then puts the next approved slot on;
    // the registration must already be out of APPROVED (or the slot cancelled)
    private void unapply(Product p, FlashSaleEvent e, LocalDateTime now) {
        if (!Objects.equals(p.getFlashSaleEventId(), e.getId())) return;
        if (p.getFlashSaleStart() != null && !now.isBefore(p.getFlashSaleStart())) {
            if (p.getFlashSaleEnd().isAfter(now)) p.setFlashSaleEnd(now);
            return;
        }
        clear(p);
        registrationRepository.flush();
        applyNext(p, now);
    }

    private static void clear(Product p) {
        p.setFlashSalePrice(null);
        p.setFlashSaleStart(null);
        p.setFlashSaleEnd(null);
        p.setFlashSaleStock(null);
        p.setFlashSaleMaxPerBuyer(null);
        p.setFlashSaleSold(0);
        p.setFlashSaleEventId(null);
    }

    // one product, one kind of flash sale at a time: the shop's own, or Shoppi slots
    private static String shopFlashProblem(Product p, LocalDateTime now) {
        String st = FlashSaleService.status(p, now);
        if (p.getFlashSaleEventId() == null && !st.equals("NONE") && !st.equals("ENDED")) {
            return "\"" + p.getName() + "\" đang có Flash Sale của shop, hãy kết thúc hoặc hủy đợt đó trước";
        }
        return null;
    }

    // ==================== seller ====================

    /** Slots open for registration (and the coming ones), with what the shop registered in each. */
    @Transactional(readOnly = true)
    public Map<String, Object> forSeller() {
        Shop shop = myShop();
        LocalDateTime now = LocalDateTime.now();
        List<FlashSaleRegistration> mine = registrationRepository.findByShopIdOrderByIdDesc(shop.getId());
        List<Map<String, Object>> events = eventRepository.upcomingOrRunning(now).stream().map(e -> {
            Map<String, Object> m = view(e, now);
            m.put("registrations", mine.stream().filter(r -> r.getEvent().getId().equals(e.getId())).map(r -> registrationView(r, now)).toList());
            m.put("used", mine.stream().filter(r -> r.getEvent().getId().equals(e.getId()) && LIVE.contains(r.getStatus())).count());
            return m;
        }).toList();
        List<Map<String, Object>> history = mine.stream().filter(r -> {
            String st = r.getEvent().status(now);
            return st.equals("ENDED") || st.equals("CANCELLED");
        }).limit(50).map(r -> {
            Map<String, Object> m = registrationView(r, now);
            m.put("eventName", r.getEvent().getName());
            m.put("eventStatus", r.getEvent().status(now));
            m.put("startAt", r.getEvent().getStartAt());
            return m;
        }).toList();
        return AdminUserService.map("events", events, "history", history);
    }

    /** items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }] */
    public List<Map<String, Object>> register(Long eventId, List<Map<String, Object>> items) {
        Shop shop = myShop();
        FlashSaleEvent e = find(eventId);
        LocalDateTime now = LocalDateTime.now();
        if (!e.status(now).equals("REGISTERING")) throw bad("Khung giờ này đã hết hạn đăng ký");
        if (items == null || items.isEmpty()) throw bad("Vui lòng chọn sản phẩm");
        long used = registrationRepository.countForShop(eventId, shop.getId(), LIVE);
        if (used + items.size() > e.getMaxProductsPerShop()) {
            throw bad("Mỗi shop đăng ký tối đa " + e.getMaxProductsPerShop() + " sản phẩm cho khung giờ này (đã đăng ký " + used + ")");
        }
        List<FlashSaleRegistration> saved = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> item : items) {
            Long productId = item.get("productId") instanceof Number n ? n.longValue() : null;
            if (productId == null || !seen.add(productId)) throw bad("Sản phẩm bị trùng hoặc không hợp lệ");
            Product p = productRepository.findById(productId).filter(x -> x.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> bad("Sản phẩm không tồn tại"));
            BigDecimal price = item.get("flashPrice") instanceof Number n ? BigDecimal.valueOf(n.longValue()) : null;
            Integer stock = item.get("flashStock") instanceof Number n ? n.intValue() : null;
            Integer perBuyer = item.get("maxPerBuyer") instanceof Number n ? n.intValue() : null;
            String problem = productProblem(p, price, stock, e);
            if (problem == null) problem = shopFlashProblem(p, now);
            if (problem != null) throw bad(problem);
            if (perBuyer != null && (perBuyer < 1 || stock != null && perBuyer > stock)) throw bad("\"" + p.getName() + "\": giới hạn mỗi khách không hợp lệ");
            if (registrationRepository.inOverlappingEvent(p.getId(), e.getId(), e.getStartAt(), e.getEndAt(), LIVE)) {
                throw bad("\"" + p.getName() + "\" đã đăng ký một khung giờ Flash Sale khác trùng thời gian");
            }
            FlashSaleRegistration r = registrationRepository.findByEventIdAndProductId(e.getId(), p.getId()).orElse(null);
            if (r != null && LIVE.contains(r.getStatus())) throw bad("\"" + p.getName() + "\" đã được đăng ký cho khung giờ này");
            if (r == null) r = FlashSaleRegistration.builder().event(e).product(p).shop(shop).build();
            // a rejected / withdrawn product can be put forward again with new terms
            r.setFlashPrice(price);
            r.setPriceAtRegistration(p.getPrice());
            r.setFlashStock(stock);
            r.setMaxPerBuyer(perBuyer);
            r.setStatus(FlashSaleRegistration.Status.PENDING);
            r.setRejectReason(null);
            r.setReviewedAt(null);
            saved.add(registrationRepository.save(r));
        }
        auditService.notifyAdmins(AdminPermission.VOUCHERS, "Đăng ký Flash Sale mới",
                shop.getName() + " đăng ký " + saved.size() + " sản phẩm cho " + e.getName(), "/admin/flash-sales?id=" + e.getId());
        return saved.stream().map(r -> registrationView(r, now)).toList();
    }

    /** Pulls a product out before the slot starts (an approved one loses its flash price). */
    public void withdraw(Long registrationId) {
        FlashSaleRegistration r = findRegistration(registrationId);
        if (!r.getShop().getId().equals(myShop().getId())) throw new ApiException("Đăng ký không tồn tại", HttpStatus.NOT_FOUND);
        LocalDateTime now = LocalDateTime.now();
        if (!LIVE.contains(r.getStatus())) throw bad("Đăng ký này không thể rút");
        if (!now.isBefore(r.getEvent().getStartAt())) throw bad("Khung giờ đã bắt đầu, không thể rút sản phẩm");
        boolean wasApproved = r.getStatus() == FlashSaleRegistration.Status.APPROVED;
        r.setStatus(FlashSaleRegistration.Status.WITHDRAWN);
        if (wasApproved) unapply(r.getProduct(), r.getEvent(), now);
    }

    // ==================== buyer ====================

    /** The home page strip: the slot running now (if any) and the next ones, with their products. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicSlots(int perSlot) {
        LocalDateTime now = LocalDateTime.now();
        return eventRepository.upcomingOrRunning(now).stream().limit(4).map(e -> publicSlot(e, now, perSlot)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicSlot(Long id, int limit) {
        FlashSaleEvent e = eventRepository.findById(id).filter(x -> !x.isCancelled())
                .orElseThrow(() -> new ApiException("Khung giờ Flash Sale không tồn tại", HttpStatus.NOT_FOUND));
        return publicSlot(e, LocalDateTime.now(), limit);
    }

    Map<String, Object> publicSlot(FlashSaleEvent e, LocalDateTime now, int limit) {
        Map<String, Object> m = AdminUserService.map("id", e.getId(), "name", e.getName(), "startAt", e.getStartAt(), "endAt", e.getEndAt(),
                "status", e.status(now));
        m.put("products", registrationRepository.forEvent(e.getId(), FlashSaleRegistration.Status.APPROVED).stream()
                .filter(r -> r.getProduct().isActive() && r.getShop().isActive())
                .sorted(Comparator.comparing((FlashSaleRegistration r) -> r.getFlashPrice().divide(r.getPriceAtRegistration(), 4, RoundingMode.HALF_UP)))
                .limit(Math.min(Math.max(limit, 1), 60))
                .map(r -> {
                    // upcoming: the card shows the coming flash price, not today's price
                    ProductResponse p = ProductResponse.from(r.getProduct());
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("product", p);
                    x.put("flashPrice", r.getFlashPrice());
                    x.put("originalPrice", r.getProduct().getPrice());
                    x.put("discountPercent", discount(r.getFlashPrice(), r.getProduct().getPrice()));
                    x.put("flashStock", r.getFlashStock());
                    x.put("flashSold", Objects.equals(r.getProduct().getFlashSaleEventId(), e.getId()) ? r.getProduct().getFlashSaleSold() : 0);
                    return x;
                }).toList());
        return m;
    }

    // ==================== helpers ====================

    private String productProblem(Product p, BigDecimal price, Integer stock, FlashSaleEvent e) {
        String name = "\"" + p.getName() + "\": ";
        if (!p.isActive() || p.getModerationStatus() != ProductModeration.APPROVED) return name + "sản phẩm đang ẩn hoặc chưa được duyệt";
        if (!p.getShop().isActive()) return name + "shop đang tạm ngừng hoạt động";
        if (price == null || price.compareTo(BigDecimal.valueOf(1000)) < 0) return name + "giá Flash Sale tối thiểu ₫1.000";
        BigDecimal ceiling = p.getPrice().multiply(BigDecimal.valueOf(100 - e.getMinDiscountPercent())).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        if (price.compareTo(ceiling) > 0) return name + "giá Flash Sale phải giảm ít nhất " + e.getMinDiscountPercent() + "% (tối đa " + NotificationService.vnd(ceiling) + ")";
        if (stock != null && (stock < 1 || stock > p.getStock())) return name + "số suất từ 1 đến " + p.getStock() + " (tồn kho)";
        if (stock == null && p.getStock() < 1) return name + "sản phẩm đã hết hàng";
        return null;
    }

    private void fill(FlashSaleEvent e, Map<String, Object> body, boolean full) {
        String name = body.get("name") == null ? null : body.get("name").toString().trim();
        if (name == null || name.length() < 3 || name.length() > 120) throw bad("Tên khung giờ từ 3 đến 120 ký tự");
        e.setName(name);
        Object cid = body.get("campaignId");
        e.setCampaign(cid instanceof Number n ? campaignRepository.findById(n.longValue()).orElseThrow(() -> bad("Chương trình không tồn tại")) : null);
        if (!full) return;
        LocalDateTime start = time(body.get("startAt"));
        LocalDateTime end = time(body.get("endAt"));
        LocalDateTime deadline = time(body.get("registrationDeadline"));
        LocalDateTime now = LocalDateTime.now();
        if (start == null || end == null || deadline == null) throw bad("Vui lòng chọn khung giờ và hạn đăng ký");
        if (!end.isAfter(start)) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (end.isAfter(start.plusHours(24))) throw bad("Một khung giờ Flash Sale kéo dài tối đa 24 giờ");
        if (deadline.isBefore(now)) throw bad("Hạn đăng ký phải ở tương lai");
        if (deadline.isAfter(start.minusHours(1))) throw bad("Hạn đăng ký phải trước giờ bắt đầu ít nhất 1 giờ (để kịp duyệt)");
        if (!eventRepository.overlapping(e.getId() == null ? -1L : e.getId(), start, end).isEmpty()) throw bad("Trùng thời gian với một khung giờ Flash Sale khác");
        int min = body.get("minDiscountPercent") instanceof Number n ? n.intValue() : 10;
        int max = body.get("maxProductsPerShop") instanceof Number n ? n.intValue() : 10;
        if (min < 5 || min > 90) throw bad("Mức giảm tối thiểu từ 5% đến 90%");
        if (max < 1 || max > 50) throw bad("Số sản phẩm mỗi shop từ 1 đến 50");
        e.setStartAt(start);
        e.setEndAt(end);
        e.setRegistrationDeadline(deadline);
        e.setMinDiscountPercent(min);
        e.setMaxProductsPerShop(max);
    }

    static Map<String, Object> view(FlashSaleEvent e, LocalDateTime now) {
        return AdminUserService.map("id", e.getId(), "name", e.getName(), "startAt", e.getStartAt(), "endAt", e.getEndAt(),
                "registrationDeadline", e.getRegistrationDeadline(), "minDiscountPercent", e.getMinDiscountPercent(),
                "maxProductsPerShop", e.getMaxProductsPerShop(), "status", e.status(now),
                "campaignId", e.getCampaign() == null ? null : e.getCampaign().getId(),
                "campaignName", e.getCampaign() == null ? null : e.getCampaign().getName(), "createdAt", e.getCreatedAt());
    }

    private static Map<String, Object> registrationView(FlashSaleRegistration r, LocalDateTime now) {
        Product p = r.getProduct();
        return AdminUserService.map("id", r.getId(), "eventId", r.getEvent().getId(), "productId", p.getId(), "productName", p.getName(),
                "thumbnailUrl", p.getThumbnailUrl(), "shopId", r.getShop().getId(), "shopName", r.getShop().getName(),
                "price", p.getPrice(), "stock", p.getStock(), "flashPrice", r.getFlashPrice(),
                "discountPercent", discount(r.getFlashPrice(), r.getPriceAtRegistration()),
                "flashStock", r.getFlashStock(), "maxPerBuyer", r.getMaxPerBuyer(), "status", r.getStatus().name(),
                "rejectReason", r.getRejectReason(), "createdAt", r.getCreatedAt(), "reviewedAt", r.getReviewedAt(),
                "sold", Objects.equals(p.getFlashSaleEventId(), r.getEvent().getId()) ? p.getFlashSaleSold() : null,
                // the shop changed the normal price since registering: the discount may no longer be real
                "priceChanged", p.getPrice().compareTo(r.getPriceAtRegistration()) != 0);
    }

    private static int discount(BigDecimal flash, BigDecimal price) {
        if (price == null || price.signum() == 0) return 0;
        return BigDecimal.ONE.subtract(flash.divide(price, 4, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    static String slot(FlashSaleEvent e) {
        return e.getStartAt().format(SLOT) + " – " + e.getEndAt().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static LocalDateTime time(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try {
            return LocalDateTime.parse(o.toString().length() == 16 ? o + ":00" : o.toString());
        } catch (Exception ex) {
            throw new ApiException("Thời gian không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    private static FlashSaleRegistration.Status parse(String s) {
        try {
            return FlashSaleRegistration.Status.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Trạng thái không hợp lệ");
        }
    }

    private FlashSaleEvent find(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new ApiException("Khung giờ Flash Sale không tồn tại", HttpStatus.NOT_FOUND));
    }

    private FlashSaleRegistration findRegistration(Long id) {
        return registrationRepository.findById(id).orElseThrow(() -> new ApiException("Đăng ký không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id()).orElseThrow(() -> bad("Bạn chưa có shop"));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
