package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "Yeu thich" products and "Theo doi" shops. */
@RestController
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // public counts; "liked" / "following" when logged in
    @GetMapping("/api/products/{id}/likes")
    public ResponseEntity<Map<String, Object>> productLikes(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(socialService.productLikes(id, principal != null ? principal.getId() : null));
    }

    @GetMapping("/api/shops/{id}/followers")
    public ResponseEntity<Map<String, Object>> shopFollowers(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(socialService.shopFollowers(id, principal != null ? principal.getId() : null));
    }

    @PostMapping("/api/buyer/likes/{productId}")
    public ResponseEntity<Map<String, Object>> like(@PathVariable Long productId) {
        return ResponseEntity.ok(socialService.like(productId));
    }

    @DeleteMapping("/api/buyer/likes/{productId}")
    public ResponseEntity<Map<String, Object>> unlike(@PathVariable Long productId) {
        return ResponseEntity.ok(socialService.unlike(productId));
    }

    @GetMapping("/api/buyer/likes")
    public ResponseEntity<PageResponse<ProductResponse>> myLikes(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(socialService.myLikes(page, size));
    }

    @PostMapping("/api/buyer/follows/{shopId}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable Long shopId) {
        return ResponseEntity.ok(socialService.follow(shopId));
    }

    @DeleteMapping("/api/buyer/follows/{shopId}")
    public ResponseEntity<Map<String, Object>> unfollow(@PathVariable Long shopId) {
        return ResponseEntity.ok(socialService.unfollow(shopId));
    }

    @GetMapping("/api/buyer/follows")
    public ResponseEntity<PageResponse<Map<String, Object>>> myFollows(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(socialService.myFollows(page, size));
    }
}
