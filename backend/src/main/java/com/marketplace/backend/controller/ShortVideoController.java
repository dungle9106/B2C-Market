package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.service.ShortVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** "Shoppi Video": the public feed, buyers' likes, the seller's videos and admin moderation. */
@RestController
@RequiredArgsConstructor
public class ShortVideoController {

    private final ShortVideoService videoService;

    // public: ?sort=hot|new&productId=&shopId=&page=&size=
    @GetMapping("/api/videos")
    public ResponseEntity<PageResponse<Map<String, Object>>> feed(@RequestParam(defaultValue = "hot") String sort,
                                                                  @RequestParam(required = false) Long productId,
                                                                  @RequestParam(required = false) Long shopId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(videoService.feed(sort, productId, shopId, page, size, principal != null ? principal.getId() : null));
    }

    @PostMapping("/api/videos/{id}/view")
    public ResponseEntity<Void> view(@PathVariable Long id) {
        videoService.view(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/buyer/videos/{id}/like")
    public ResponseEntity<Map<String, Object>> like(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.like(id));
    }

    @DeleteMapping("/api/buyer/videos/{id}/like")
    public ResponseEntity<Map<String, Object>> unlike(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.unlike(id));
    }

    // ---- seller ----

    @GetMapping("/api/seller/videos")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(videoService.mine());
    }

    // body: { videoUrl, caption, productIds: [] }
    @PostMapping("/api/seller/videos")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(videoService.create(str(body.get("videoUrl")), str(body.get("caption")), ids(body.get("productIds"))));
    }

    // body: { caption, productIds: [] }
    @PutMapping("/api/seller/videos/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(videoService.update(id, str(body.get("caption")), ids(body.get("productIds"))));
    }

    @DeleteMapping("/api/seller/videos/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        videoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- admin (PERM_PRODUCTS in SecurityConfig) ----

    @GetMapping("/api/admin/videos")
    public ResponseEntity<PageResponse<Map<String, Object>>> adminList(@RequestParam(defaultValue = "ACTIVE") String status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(videoService.adminList(status, page, size));
    }

    // body: { reason }
    @PostMapping("/api/admin/videos/{id}/hide")
    public ResponseEntity<Map<String, Object>> hide(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(videoService.hide(id, body.get("reason")));
    }

    @PostMapping("/api/admin/videos/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.restore(id));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<Long> ids(Object o) {
        if (o == null) return List.of();
        if (!(o instanceof List<?> list)) throw new ApiException("productIds phải là danh sách", HttpStatus.BAD_REQUEST);
        try {
            return list.stream().map(x -> Long.valueOf(String.valueOf(x))).toList();
        } catch (NumberFormatException e) {
            throw new ApiException("Mã sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }
}
