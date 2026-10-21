package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.AddToCartRequest;
import com.marketplace.backend.dto.response.CartItemResponse;
import com.marketplace.backend.entity.ProductEvent;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.VisitorKey;
import com.marketplace.backend.service.CartService;
import com.marketplace.backend.service.ProductEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final ProductEventService eventService;

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCart() {
        return ResponseEntity.ok(cartService.getCart());
    }

    @PostMapping
    public ResponseEntity<CartItemResponse> addItem(@Valid @RequestBody AddToCartRequest request, HttpServletRequest http) {
        CartItemResponse item = cartService.addItem(request);
        Long userId = CurrentUser.id();
        eventService.record(ProductEvent.Type.ADD_TO_CART, item.getProductId(), item.getShopId(), userId, VisitorKey.of(http, userId));
        return ResponseEntity.ok(item);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CartItemResponse> updateQuantity(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return ResponseEntity.ok(cartService.updateQuantity(id, body.getOrDefault("quantity", 1)));
    }

    // DELETE /api/cart?ids=1,2,3
    @DeleteMapping
    public ResponseEntity<Void> removeItems(@RequestParam List<Long> ids) {
        cartService.removeItems(ids);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeItem(@PathVariable Long id) {
        cartService.removeItem(id);
        return ResponseEntity.noContent().build();
    }
}
