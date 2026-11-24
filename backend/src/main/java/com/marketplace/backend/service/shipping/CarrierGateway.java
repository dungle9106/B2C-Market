package com.marketplace.backend.service.shipping;

import com.marketplace.backend.entity.Address;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.service.ShippingService.ShippingOption;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A carrier connected through its real API (GHN, GHTK...). Carriers without one stay on the simulated rates and
 * tracking (ShippingService, TrackingService). Each gateway prices its own options at checkout, books the parcel
 * when the seller prepares the order, cancels the booking, and is configured on the admin page.
 */
public interface CarrierGateway {

    /** The carrier code in ShippingService.CARRIERS, also stored as Order.shippingGateway. */
    String code();

    boolean ready();

    /** Re-prices (or removes) this carrier's options with its own fee API; unchanged when it can't quote. */
    List<ShippingOption> adjustOptions(List<ShippingOption> options, Shop shop, Address to, int quantity, BigDecimal value);

    /** Can this order be booked with the carrier? */
    boolean handles(Order o);

    record Booking(String trackingNumber, BigDecimal fee, LocalDateTime expectedDelivery, String status) {
    }

    /** Books the parcel; throws with the carrier's reason. */
    Booking createOrder(Order o);

    /** Cancels a booking before pick-up; failures are only logged. */
    void cancel(String trackingNumber);

    Map<String, Object> config();

    Map<String, Object> update(Map<String, Object> body);

    Map<String, Object> test();
}
