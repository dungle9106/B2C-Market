package com.marketplace.backend.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Puts the id of each new e-mail on the RabbitMQ queue once its row is committed. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.rabbit", havingValue = "true")
public class EmailQueuePublisher {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(EmailQueuedEvent e) {
        try {
            rabbit.convertAndSend(RabbitConfig.EMAIL_QUEUE, String.valueOf(e.messageId()));
        } catch (Exception ex) {
            // broker down: the scheduler sends it from the database a little later
            log.warn("RabbitMQ unavailable, e-mail #{} left to the scheduler: {}", e.messageId(), ex.getMessage());
        }
    }
}
