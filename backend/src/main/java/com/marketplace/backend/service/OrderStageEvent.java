package com.marketplace.backend.service;

/** An order reached a stage the buyer is told about (published by NotificationService.onOrderEvent). */
public record OrderStageEvent(Long orderId, String stage, String detail) {
}
