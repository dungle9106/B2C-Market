package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.DashboardStatsResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderItem;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ReturnStatus;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductQuestionRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.SellerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private static final Set<OrderStatus> CANCELLED_OR_RETURNED =
            EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURN_REQUESTED, OrderStatus.REFUNDED);
    // orders that don't count as sales in the day-by-day analytics
    private static final Set<OrderStatus> NOT_A_SALE = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.AWAITING_PAYMENT);

    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductQuestionRepository questionRepository;
    private final ReturnRequestRepository returnRepository;
    private final SellerAnalyticsService analyticsService;

    // readOnly transaction: order items are lazily loaded (open-in-view is off)
    @Transactional(readOnly = true)
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> stats() {
        Shop shop = shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));

        List<Order> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shop.getId());
        List<Product> products = productRepository.findByShopId(shop.getId());

        BigDecimal revenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        List<Order> todaySales = salesOn(orders, today);
        List<Order> yesterdaySales = salesOn(orders, today.minusDays(1));

        List<ProductResponse> topProducts = products.stream()
                .sorted(Comparator.comparing(p -> -p.getSoldCount()))
                .limit(5)
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        long[] traffic = analyticsService.visitorsTodayAndYesterday(shop.getId());

        return ResponseEntity.ok(DashboardStatsResponse.builder()
                .totalRevenue(revenue)
                .totalOrders(orders.size())
                .totalProducts(products.size())
                .pendingOrders(countStatus(orders, OrderStatus.PENDING))
                .toShipOrders(countStatus(orders, OrderStatus.CONFIRMED))
                .shippingOrders(countStatus(orders, OrderStatus.SHIPPING))
                .cancelledOrReturnedOrders(orders.stream().filter(o -> CANCELLED_OR_RETURNED.contains(o.getStatus())).count())
                .outOfStockProducts(products.stream().filter(p -> p.isActive() && p.getStock() != null && p.getStock() <= 0).count())
                .hiddenProducts(products.stream().filter(p -> !p.isActive()).count())
                .pendingReturns(returnRepository.countByOrderShopIdAndStatus(shop.getId(), ReturnStatus.REQUESTED))
                .unansweredQuestions(questionRepository.countByProductShopIdAndAnswerIsNullAndHiddenFalse(shop.getId()))
                .todaySales(sum(todaySales))
                .yesterdaySales(sum(yesterdaySales))
                .todayOrders(todaySales.size())
                .yesterdayOrders(yesterdaySales.size())
                .todayItemsSold(itemsSold(todaySales))
                .yesterdayItemsSold(itemsSold(yesterdaySales))
                .todayVisitors(traffic[0])
                .yesterdayVisitors(traffic[1])
                .todayViews(traffic[2])
                .yesterdayViews(traffic[3])
                .todayBuyers(todaySales.stream().map(o -> o.getBuyer().getId()).distinct().count())
                .yesterdayBuyers(yesterdaySales.stream().map(o -> o.getBuyer().getId()).distinct().count())
                .topProducts(topProducts)
                .build());
    }

    private static long countStatus(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(o -> o.getStatus() == status).count();
    }

    private static List<Order> salesOn(List<Order> orders, LocalDate day) {
        return orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().equals(day))
                .filter(o -> !NOT_A_SALE.contains(o.getStatus()))
                .collect(Collectors.toList());
    }

    private static BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long itemsSold(List<Order> orders) {
        return orders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToLong(OrderItem::getQuantity)
                .sum();
    }
}
