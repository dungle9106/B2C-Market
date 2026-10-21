package com.marketplace.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketplace.backend.service.GhnWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final GhnWebhookService ghnWebhook;

    // GHN's status callbacks (public; the secret key in the URL is checked)
    @PostMapping("/ghn/webhook")
    public ResponseEntity<Map<String, Object>> ghnWebhook(@RequestParam(required = false) String key, @RequestBody JsonNode body) {
        return ResponseEntity.ok(ghnWebhook.handle(key, body));
    }
}
