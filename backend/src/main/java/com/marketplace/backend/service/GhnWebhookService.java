package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * GHN's status callbacks ("webhook"): each status change of a booked parcel becomes a step of the order timeline,
 * and pick-up / delivery / return move the order itself. Repeated callbacks with the same status are ignored.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GhnWebhookService {

    private final GhnService ghnService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final TrackingService trackingService;

    public Map<String, Object> handle(String key, JsonNode body) {
        if (!ghnService.webhookKeyMatches(key)) throw new ApiException("Sai khóa webhook", HttpStatus.FORBIDDEN);
        String code = body.path("OrderCode").asText(null);
        String status = body.path("Status").asText("").toLowerCase();
        if (code == null || status.isBlank()) throw new ApiException("Thiếu OrderCode/Status", HttpStatus.BAD_REQUEST);
        Order o = orderRepository.findFirstByTrackingNumberAndShippingGateway(code, "GHN").orElse(null);
        if (o == null) {
            log.info("GHN webhook for unknown order {}", code);
            return Map.of("ok", true, "ignored", "unknown order");
        }
        if (status.equals(o.getGatewayStatus())) return Map.of("ok", true, "ignored", "duplicate");
        o.setGatewayStatus(status);
        String warehouse = body.path("Warehouse").asText(null);
        String reason = body.path("Reason").asText(body.path("Description").asText(""));
        String from = o.getShop().getProvince(), to = o.getShippingProvince();

        switch (status) {
            case "picking" -> trackingService.add(o, "PICKING", "Shipper GHN đang đến lấy hàng", from);
            case "picked" -> orderService.carrierPickedUp(o);
            case "storing" -> trackingService.add(o, "AT_ORIGIN_HUB", "Đơn hàng đã nhập kho GHN" + at(warehouse), from);
            case "transporting" -> trackingService.add(o, "IN_TRANSIT", "Đơn hàng đang được GHN trung chuyển" + at(warehouse), from);
            case "sorting" -> trackingService.add(o, "AT_DEST_HUB", "Đơn hàng đang được phân loại" + at(warehouse), to);
            case "delivering", "money_collect_delivering" -> {
                if (o.getStatus() == OrderStatus.CONFIRMED) orderService.carrierPickedUp(o);
                trackingService.add(o, "OUT_FOR_DELIVERY", "Đơn hàng đang được GHN giao đến bạn", to);
            }
            case "delivered" -> orderService.carrierDelivered(o);
            case "delivery_fail" -> trackingService.add(o, "DELIVERY_FAILED",
                    "Giao hàng không thành công" + (reason.isBlank() ? "" : ": " + reason), to);
            case "waiting_to_return", "return", "return_transporting", "return_sorting", "returning" ->
                    trackingService.add(o, "RETURNING", "Giao hàng không thành công, đơn hàng đang được hoàn về người bán", to);
            case "returned" -> {
                trackingService.add(o, "RETURNED_TO_SELLER", "GHN đã hoàn trả đơn hàng về người bán", from);
                if (o.getStatus() == OrderStatus.SHIPPING) orderService.cancel(o, "SYSTEM", "Giao hàng không thành công, hàng đã hoàn về người bán");
            }
            case "cancel" -> {
                if (o.getStatus() == OrderStatus.CONFIRMED) {
                    // GHN dropped the booking before pick-up: the seller can prepare the order again
                    trackingService.add(o, "CARRIER_CANCELLED", "GHN đã hủy vận đơn " + code + (reason.isBlank() ? "" : " (" + reason + ")")
                            + ". Người bán sẽ tạo lại vận đơn", from);
                    o.setTrackingNumber(null);
                    o.setShippingGateway(null);
                    o.setGatewayStatus(null);
                    o.setPackedAt(null);
                }
            }
            case "lost", "damage", "exception" -> trackingService.add(o, "CARRIER_ISSUE",
                    "GHN báo sự cố với đơn hàng" + (reason.isBlank() ? "" : ": " + reason) + ". Shoppi đang phối hợp xử lý", null);
            default -> {
                // ready_to_pick and anything new: only remembered
            }
        }
        orderRepository.save(o);
        return Map.of("ok", true, "status", status);
    }

    private static String at(String warehouse) {
        return warehouse == null || warehouse.isBlank() ? "" : " (" + warehouse + ")";
    }
}
