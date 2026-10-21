package com.marketplace.backend.messaging;

import com.marketplace.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sends the e-mails announced on the queue (EmailService.sendNow is idempotent). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.rabbit", havingValue = "true")
public class EmailQueueConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void onMessage(String id) {
        try {
            emailService.sendNow(Long.valueOf(id.trim()));
        } catch (NumberFormatException ignored) {
            // not one of ours: dropped
        }
    }
}
