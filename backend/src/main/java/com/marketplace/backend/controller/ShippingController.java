package com.marketplace.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketplace.backend.service.GhnWebhookService;
import com.marketplace.backend.service.shipping.GhtkWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final GhnWebhookService ghnWebhook;
    private final GhtkWebhookService ghtkWebhook;

    // GHN's status callbacks (public; the secret key in the URL is checked)
    @PostMapping("/ghn/webhook")
    public ResponseEntity<Map<String, Object>> ghnWebhook(@RequestParam(required = false) String key, @RequestBody JsonNode body) {
        return ResponseEntity.ok(ghnWebhook.handle(key, body));
    }

    // GHTK's status callbacks: sent form-encoded (some accounts send JSON)
    @PostMapping(value = "/ghtk/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> ghtkWebhookForm(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(ghtkWebhook.handle(params.get("key"), params));
    }

    @PostMapping(value = "/ghtk/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ghtkWebhookJson(@RequestParam(required = false) String key, @RequestBody JsonNode body) {
        Map<String, String> fields = new LinkedHashMap<>();
        body.fields().forEachRemaining(e -> fields.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
        return ResponseEntity.ok(ghtkWebhook.handle(key, fields));
    }
}
