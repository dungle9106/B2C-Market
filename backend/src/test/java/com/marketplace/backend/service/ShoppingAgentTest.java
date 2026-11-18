package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.AddToCartRequest;
import com.marketplace.backend.dto.response.CartItemResponse;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.service.ai.Agent;
import com.marketplace.backend.service.reco.RecommendationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The agent loop with a scripted "model" instead of a real API: tools run, results go back, actions come out. */
class ShoppingAgentTest {

    private final AIService ai = mock(AIService.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CartService cart = mock(CartService.class);
    private final VoucherWalletService wallet = mock(VoucherWalletService.class);
    private final RecommendationService reco = mock(RecommendationService.class);
    private final ShoppingAgent agent = new ShoppingAgent(ai, products, orders, cart, wallet, reco, mock(PlatformTransactionManager.class));
    private final ShoppingAgent.ProductSearch noSearch = (q, min, max, limit) -> List.of();

    private static Agent.Reply calls(Agent.Call... calls) {
        return new Agent.Reply(null, List.of(calls), 100, 10, null);
    }

    private static Agent.Reply text(String text) {
        return new Agent.Reply(text, List.of(), 100, 10, null);
    }

    @SuppressWarnings("unchecked")
    private List<Agent.Msg> lastConversation() {
        ArgumentCaptor<List<Agent.Msg>> captor = ArgumentCaptor.forClass(List.class);
        verify(ai, atLeastOnce()).converse(anyString(), captor.capture(), anyList());
        return captor.getValue();
    }

    @Test
    void addsToCartWhenTheModelAsksAndReportsTheAction() {
        CartItemResponse item = new CartItemResponse();
        item.setProductName("Áo thun basic");
        item.setVariantName("Đen / L");
        item.setQuantity(2);
        when(cart.addItem(any())).thenReturn(item);
        when(ai.converse(anyString(), anyList(), anyList())).thenReturn(
                calls(new Agent.Call("c1", "add_to_cart", Map.of("product_id", 5, "variant_id", 9, "quantity", 2))),
                text("Mình đã thêm 2 Áo thun basic (Đen / L) vào giỏ."));

        ShoppingAgent.Result r = agent.run(1L, "thêm 2 cái áo đen size L vào giỏ", List.of(), noSearch);

        ArgumentCaptor<AddToCartRequest> req = ArgumentCaptor.forClass(AddToCartRequest.class);
        verify(cart).addItem(req.capture());
        assertEquals(5L, req.getValue().getProductId());
        assertEquals(9L, req.getValue().getVariantId());
        assertEquals(2, req.getValue().getQuantity());
        assertEquals("Mình đã thêm 2 Áo thun basic (Đen / L) vào giỏ.", r.reply());
        assertEquals("CART_ADDED", r.actions().get(0).get("type"));
        assertEquals(List.of("Thêm vào giỏ hàng"), r.steps());
        // the tool's result went back to the model with the same call id
        Agent.ToolResult result = (Agent.ToolResult) lastConversation().stream().filter(m -> m instanceof Agent.ToolResult).findFirst().orElseThrow();
        assertEquals("c1", result.callId());
        assertTrue(result.content().contains("\"ok\":true"));
    }

    @Test
    void neverCancelsAnOrderItselfOnlyProposesAButton() {
        Order order = Order.builder().id(77L).code("SP123").status(OrderStatus.PENDING)
                .buyer(User.builder().id(1L).build()).shop(Shop.builder().name("Shop A").build()).build();
        when(orders.findByCode("SP123")).thenReturn(Optional.of(order));
        when(ai.converse(anyString(), anyList(), anyList())).thenReturn(
                calls(new Agent.Call("c1", "propose_cancel_order", Map.of("order_code", "SP123", "reason", "đặt nhầm"))),
                text("Bạn bấm nút xác nhận bên dưới để hủy đơn SP123 nhé."));

        ShoppingAgent.Result r = agent.run(1L, "hủy đơn SP123 giúp mình", List.of(), noSearch);

        Map<String, Object> action = r.actions().get(0);
        assertEquals("CANCEL_ORDER", action.get("type"));
        assertEquals(77L, action.get("orderId"));
        assertEquals("CANCEL", action.get("mode"));
        assertEquals("đặt nhầm", action.get("reason"));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void someoneElsesOrderIsAnErrorForTheModelNotAnAction() {
        Order order = Order.builder().id(77L).code("SP999").status(OrderStatus.PENDING)
                .buyer(User.builder().id(2L).build()).shop(Shop.builder().name("Shop A").build()).build();
        when(orders.findByCode("SP999")).thenReturn(Optional.of(order));
        when(ai.converse(anyString(), anyList(), anyList())).thenReturn(
                calls(new Agent.Call("c1", "propose_cancel_order", Map.of("order_code", "SP999"))),
                text("Mình không tìm thấy đơn SP999 trong tài khoản của bạn."));

        ShoppingAgent.Result r = agent.run(1L, "hủy đơn SP999", List.of(), noSearch);

        assertTrue(r.actions().isEmpty());
        Agent.ToolResult result = (Agent.ToolResult) lastConversation().stream().filter(m -> m instanceof Agent.ToolResult).findFirst().orElseThrow();
        assertTrue(result.content().contains("error"));
    }

    @Test
    void aFailedModelCallFallsBackToTheOldChatbot() {
        when(ai.converse(anyString(), anyList(), anyList())).thenReturn(Agent.Reply.failed("HTTP 401"));
        assertNull(agent.run(1L, "xin chào", List.of(), noSearch));
    }

    @Test
    void stopsAfterTooManyToolRounds() {
        when(ai.converse(anyString(), anyList(), anyList()))
                .thenReturn(calls(new Agent.Call("c", "handoff_to_human", Map.of())));
        assertNull(agent.run(1L, "?", List.of(), noSearch));
        verify(ai, times(ShoppingAgent.MAX_STEPS)).converse(anyString(), anyList(), anyList());
    }

    @Test
    void offersEveryToolWithAnObjectSchema() {
        assertEquals(9, ShoppingAgent.TOOLS.size());
        ShoppingAgent.TOOLS.forEach(t -> assertEquals("object", t.schema().get("type"), t.name()));
    }
}
