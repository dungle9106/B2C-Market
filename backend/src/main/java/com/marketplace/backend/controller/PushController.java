package com.marketplace.backend.controller;

import com.marketplace.backend.push.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Push notifications: the browser / phone of the signed-in user registers here. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService service;

    // { publicKey } - the VAPID key a browser subscribes with
    @GetMapping("/web-config")
    public ResponseEntity<Map<String, Object>> webConfig() {
        return ResponseEntity.ok(service.webConfig());
    }

    // body: { kind: WEB|EXPO, endpoint, p256dh?, auth?, app?, device? }
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.subscribe(body));
    }

    // body: { endpoint }
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, Object> body) {
        service.unsubscribe(body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        return ResponseEntity.ok(service.test());
    }
}
