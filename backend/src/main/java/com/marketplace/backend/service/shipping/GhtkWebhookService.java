package com.marketplace.backend.service.shipping;

import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.service.OrderService;
import com.marketplace.backend.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * GHTK's status callbacks (form or JSON: label_id, partner_id, status_id, reason...). Each official status becomes
 * a step of the order timeline, and pick-up / delivery / return move the order itself. The shipper's own reports
 * (45, 49, 123, 127, 128, 410) are informational only and may be corrected later, so they are not acted on.
 * Status codes: api.ghtk.vn, "Webhook".
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GhtkWebhookService {

    private static final Set<String> SHIPPER_REPORTS = Set.of("45", "49", "123", "127", "128", "410");

    private final GhtkService ghtkService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final TrackingService trackingService;

    public Map<String, Object> handle(String key, Map<String, String> body) {
        if (!ghtkService.webhookKeyMatches(key)) throw new ApiException("Sai khóa webhook", HttpStatus.FORBIDDEN);
        String label = body.get("label_id");
        String status = body.getOrDefault("status_id", "").trim();
        if (label == null || label.isBlank() || status.isBlank()) throw new ApiException("Thiếu label_id/status_id", HttpStatus.BAD_REQUEST);
        Order o = orderRepository.findFirstByTrackingNumberAndShippingGateway(label, GhtkService.CODE)
                // partner_id = the order code, "-2", "-3"... for a booking made again after GHTK cancelled one
                .or(() -> body.get("partner_id") == null ? java.util.Optional.empty()
                        : orderRepository.findByCode(body.get("partner_id").replaceFirst("-\\d$", ""))
                        .filter(x -> GhtkService.CODE.equals(x.getShippingGateway()) && label.equals(x.getTrackingNumber())))
                .orElse(null);
        if (o == null) {
            log.info("GHTK webhook for unknown order {}", label);
            return Map.of("ok", true, "ignored", "unknown order");
        }
        if (SHIPPER_REPORTS.contains(status)) return Map.of("ok", true, "ignored", "shipper report");
        if (status.equals(o.getGatewayStatus())) return Map.of("ok", true, "ignored", "duplicate");
        o.setGatewayStatus(status);
        String reason = body.getOrDefault("reason", "").trim();
        String why = reason.isBlank() ? "" : ": " + reason;
        String from = o.getShop().getProvince(), to = o.getShippingProvince();

        switch (status) {
            case "12" -> trackingService.add(o, "PICKING", "Shipper GHTK đang đến lấy hàng", from);
            case "3" -> {
                orderService.carrierPickedUp(o);
                trackingService.add(o, "AT_ORIGIN_HUB", "GHTK đã lấy hàng và nhập kho", from);
            }
            case "7", "8" -> trackingService.add(o, "CARRIER_ISSUE",
                    ("7".equals(status) ? "GHTK chưa lấy được hàng" : "GHTK hoãn lấy hàng") + why, from);
            case "4" -> {
                if (o.getStatus() == OrderStatus.CONFIRMED) orderService.carrierPickedUp(o);
                trackingService.add(o, "OUT_FOR_DELIVERY", "Đơn hàng đang được GHTK giao đến bạn", to);
            }
            case "5", "6" -> orderService.carrierDelivered(o);
            case "9", "10" -> trackingService.add(o, "DELIVERY_FAILED",
                    ("9".equals(status) ? "Giao hàng không thành công" : "GHTK hoãn giao hàng") + why, to);
            case "20" -> trackingService.add(o, "RETURNING", "Giao hàng không thành công, đơn hàng đang được hoàn về người bán", to);
            case "21", "11" -> {
                trackingService.add(o, "RETURNED_TO_SELLER", "GHTK đã hoàn trả đơn hàng về người bán", from);
                if (o.getStatus() == OrderStatus.SHIPPING) orderService.cancel(o, "SYSTEM", "Giao hàng không thành công, hàng đã hoàn về người bán");
            }
            case "-1" -> {
                if (o.getStatus() == OrderStatus.CONFIRMED) {
                    // GHTK dropped the booking before pick-up: the seller can prepare the order again
                    trackingService.add(o, "CARRIER_CANCELLED", "GHTK đã hủy vận đơn " + label + (reason.isBlank() ? "" : " (" + reason + ")")
                            + ". Người bán sẽ tạo lại vận đơn", from);
                    o.setTrackingNumber(null);
                    o.setShippingGateway(null);
                    o.setGatewayStatus(null);
                    o.setPackedAt(null);
                }
            }
            case "13" -> trackingService.add(o, "CARRIER_ISSUE", "GHTK báo sự cố với đơn hàng (đơn bồi hoàn)" + why + ". Shoppi đang phối hợp xử lý", null);
            default -> {
                // 1 (not received yet), 2 (received) and anything new: only remembered
            }
        }
        orderRepository.save(o);
        return Map.of("ok", true, "status", status);
    }
}
