package com.marketplace.backend.service.shipping;

import com.marketplace.backend.entity.Address;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.AdminUserService;
import com.marketplace.backend.service.ShippingService;
import com.marketplace.backend.service.ShippingService.ShippingOption;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** The carriers connected through a real API, looked up by code. */
@Service
@RequiredArgsConstructor
public class CarrierGateways {

    private final List<CarrierGateway> gateways;

    public Optional<CarrierGateway> byCode(String code) {
        return gateways.stream().filter(g -> g.code().equals(code)).findFirst();
    }

    public CarrierGateway require(String code) {
        return byCode(code).orElseThrow(() -> new ApiException("Đơn vị vận chuyển không hỗ trợ kết nối API", HttpStatus.NOT_FOUND));
    }

    /** Every connected carrier prices its own options (the simulated table stays for the others). */
    public List<ShippingOption> adjustOptions(List<ShippingOption> options, Shop shop, Address to, int quantity, BigDecimal value) {
        for (CarrierGateway g : gateways) options = g.adjustOptions(options, shop, to, quantity, value);
        return options;
    }

    /** The gateway that will book this order, if its carrier is connected. */
    public Optional<CarrierGateway> forOrder(Order o) {
        return byCode(o.getCarrier()).filter(g -> g.handles(o));
    }

    /** Cancels the booking of an order not picked up yet (no-op for simulated parcels). */
    public void cancelBooking(Order o) {
        if (o.getShippingGateway() == null || o.getTrackingNumber() == null) return;
        byCode(o.getShippingGateway()).ifPresent(g -> g.cancel(o.getTrackingNumber()));
    }

    /** Admin overview: each carrier, its services and whether it runs on a real API. */
    public List<Map<String, Object>> overview() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShippingService.Carrier c : ShippingService.CARRIERS) {
            Optional<CarrierGateway> g = byCode(c.code());
            list.add(AdminUserService.map("code", c.code(), "name", c.name(),
                    "methods", c.methods().stream().sorted().map(ShippingService::methodName).toList(),
                    "api", g.isPresent(), "connected", g.map(CarrierGateway::ready).orElse(false),
                    "note", c.note()));
        }
        return list;
    }
}
