package com.marketplace.backend.messaging;

/** An e-mail was stored in email_messages and waits to be sent. */
public record EmailQueuedEvent(Long messageId) {
}
