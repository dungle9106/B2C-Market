package com.marketplace.backend.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.PushSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Sends push notifications to the Shoppi phone apps through Expo's push service, which forwards them to
 * Firebase Cloud Messaging (Android) / APNs (iOS). Needs no key on this side: the app's Expo project holds them.
 */
@Component
@RequiredArgsConstructor
public class ExpoPushSender {

    private static final URI URL = URI.create("https://exp.host/--/api/v2/push/send");

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Sends one message to these tokens; returns the tokens Expo says are no longer registered. */
    public Set<String> send(List<PushSubscription> targets, String title, String body, Map<String, Object> data) {
        Set<String> gone = new HashSet<>();
        for (int i = 0; i < targets.size(); i += 100) { // Expo accepts up to 100 messages per request
            List<PushSubscription> batch = targets.subList(i, Math.min(i + 100, targets.size()));
            List<Map<String, Object>> messages = new ArrayList<>();
            for (PushSubscription s : batch) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("to", s.getEndpoint());
                m.put("title", title);
                m.put("body", body);
                m.put("data", data);
                m.put("sound", "default");
                m.put("channelId", "default");
                m.put("priority", "high");
                messages.add(m);
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URL)
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(messages)))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode tickets = json.readTree(res.body()).path("data");
                for (int k = 0; k < batch.size() && k < tickets.size(); k++) {
                    if ("DeviceNotRegistered".equals(tickets.get(k).path("details").path("error").asText()))
                        gone.add(batch.get(k).getEndpoint());
                }
            } catch (Exception ignored) {
                // Expo unreachable: the in-app notification is still there
            }
        }
        return gone;
    }
}
