package com.marketplace.backend.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ (RABBITMQ_ENABLED=true, RABBITMQ_URL=amqp://user:pass@host:5672): e-mails are handed to a durable queue and
 * sent by a consumer at once, instead of waiting for the 5-second database poll. The database row stays the record
 * of each e-mail (status, retries, outbox page); RabbitMQ only carries its id.
 */
@Configuration
@ConditionalOnProperty(name = "app.messaging.rabbit", havingValue = "true")
public class RabbitConfig {

    public static final String EMAIL_QUEUE = "shoppi.email";

    @Bean
    public Queue emailQueue() {
        return new Queue(EMAIL_QUEUE, true);
    }
}
