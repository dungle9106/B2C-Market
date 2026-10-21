package com.marketplace.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// Everything the "Kenh Nguoi Ban" overview page shows, in one call.
@Data
@Builder
public class DashboardStatsResponse {
    // ---- totals ----
    private BigDecimal totalRevenue;      // sum of DELIVERED orders
    private long totalOrders;
    private long totalProducts;

    // ---- "Danh sach can lam" (to-do counters) ----
    private long pendingOrders;           // PENDING: cho xac nhan
    private long toShipOrders;            // CONFIRMED: cho lay hang
    private long shippingOrders;          // SHIPPING: dang giao
    private long cancelledOrReturnedOrders; // CANCELLED / RETURN_REQUESTED / REFUNDED
    private long outOfStockProducts;
    private long hiddenProducts;
    private long unansweredQuestions;
    private long pendingReturns;          // return/refund requests waiting for the seller     // product Q&A waiting for the seller

    // ---- "Phan tich ban hang": today vs yesterday, by order creation time,
    // excluding cancelled/refunded orders ----
    private BigDecimal todaySales;
    private BigDecimal yesterdaySales;
    private long todayOrders;
    private long yesterdayOrders;
    private long todayItemsSold;
    private long yesterdayItemsSold;
    // product page traffic (unique visitors / views) and buyers, for the conversion rate
    private long todayVisitors;
    private long yesterdayVisitors;
    private long todayViews;
    private long yesterdayViews;
    private long todayBuyers;
    private long yesterdayBuyers;

    private List<ProductResponse> topProducts;
}
