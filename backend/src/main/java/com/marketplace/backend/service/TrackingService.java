package com.marketplace.backend.service;

import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.TrackingEvent;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order timeline ("Theo doi don hang"). Real steps (placed, paid, confirmed,
 * delivered, cancelled, refunds) are recorded when they happen. Carrier scans
 * between pick-up and delivery are SIMULATED (there is no carrier API): once an
 * order is SHIPPING, a new scan appears every app.shipping.sim-step-seconds,
 * timed from when the parcel was handed over.
 *
 * Every recorded step is also passed to NotificationService, which decides who
 * (buyer / seller) gets an in-app notification about it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TrackingService {

    private final TrackingEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Value("${app.shipping.sim-step-seconds:60}")
    private long stepSeconds;

    public void add(Order order, String stage, String title, String location) {
        eventRepository.save(TrackingEvent.builder()
                .order(order).stage(stage).title(title).location(location)
                .createdAt(LocalDateTime.now())
                .build());
        notificationService.onOrderEvent(order, stage, title);
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> events(Long orderId) {
        return eventRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
    }

    /** Carrier code + 9 digits, e.g. GHN482910375. */
    public static String newTrackingNumber(String carrier) {
        return (carrier != null ? carrier : "SP") + (100_000_000 + new Random().nextInt(900_000_000));
    }

    // ---- carrier simulator ----

    private record Scan(String stage, String title, String location) {}

    private List<Scan> plan(Order o) {
        String from = o.getShop().getProvince() != null ? o.getShop().getProvince() : "kho người bán";
        String to = o.getShippingProvince() != null ? o.getShippingProvince() : "khu vực giao hàng";
        boolean sameProvince = ShippingService.sameProvince(from, to);
        List<Scan> scans = new ArrayList<>();
        scans.add(new Scan("AT_ORIGIN_HUB", "Đơn hàng đã đến bưu cục " + from, from));
        if (!sameProvince) {
            scans.add(new Scan("IN_TRANSIT", "Đơn hàng đã rời kho phân loại " + from + ", đang trung chuyển", from));
            scans.add(new Scan("AT_DEST_HUB", "Đơn hàng đã đến kho " + to, to));
        }
        String[] shippers = {"Nguyễn Văn An", "Trần Minh Đức", "Lê Hoàng Nam", "Phạm Quốc Bảo", "Võ Thanh Tùng"};
        String shipper = shippers[(int) (o.getId() % shippers.length)];
        scans.add(new Scan("OUT_FOR_DELIVERY", "Đơn hàng đang được giao đến bạn. Shipper " + shipper
                + " - 09" + String.format("%08d", (o.getId() * 7919) % 100_000_000) + " sẽ liên hệ trước khi giao", to));
        return scans;
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void advanceShipments() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> shipping = orderRepository.findByStatus(OrderStatus.SHIPPING).stream()
                // parcels booked with a real carrier (GHN) are tracked by its webhooks instead
                .filter(o -> o.getShippedAt() != null && o.getShippingGateway() == null).toList();
        if (shipping.isEmpty()) return;

        Map<Long, Set<String>> done = eventRepository.findByOrderIdIn(shipping.stream().map(Order::getId).toList())
                .stream().collect(Collectors.groupingBy(e -> e.getOrder().getId(),
                        Collectors.mapping(TrackingEvent::getStage, Collectors.toSet())));

        for (Order o : shipping) {
            List<Scan> scans = plan(o);
            for (int i = 0; i < scans.size(); i++) {
                LocalDateTime due = o.getShippedAt().plusSeconds(stepSeconds * (i + 1));
                Scan s = scans.get(i);
                if (due.isAfter(now)) break;
                if (done.getOrDefault(o.getId(), Set.of()).contains(s.stage())) continue;
                eventRepository.save(TrackingEvent.builder()
                        .order(o).stage(s.stage()).title(s.title()).location(s.location())
                        .createdAt(due)
                        .build());
                notificationService.onOrderEvent(o, s.stage(), s.title());
            }
        }
    }
}
