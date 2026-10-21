package com.marketplace.backend.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes RealtimeEvents to each user's private queue (/user/queue/events) once the transaction that created them
 * has committed, so a client reloading on the event always finds the new data. Users who are not connected simply
 * miss the push: the pages still poll (slowly) as a fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String QUEUE = "/queue/events";

    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void push(RealtimeEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", e.type());
        if (e.data() != null) payload.putAll(e.data());
        for (Long id : e.userIds()) {
            if (id == null) continue;
            try {
                messaging.convertAndSendToUser(String.valueOf(id), QUEUE, payload);
            } catch (Exception ex) {
                log.debug("Realtime push to {} failed: {}", id, ex.getMessage());
            }
        }
    }
}
