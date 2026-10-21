package com.marketplace.backend.realtime;

import java.util.Collection;
import java.util.Map;

/**
 * Something a logged-in user should see at once (new chat message, new notification...).
 * Published inside a transaction; RealtimePublisher pushes it over WebSocket after the commit.
 *
 * @param userIds who receives it
 * @param type    "chat" | "notification"
 * @param data    small payload; the client reloads what it needs through the REST API
 */
public record RealtimeEvent(Collection<Long> userIds, String type, Map<String, Object> data) {
}
