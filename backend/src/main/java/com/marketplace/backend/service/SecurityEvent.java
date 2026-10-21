package com.marketplace.backend.service;

import java.util.Map;

/**
 * Something the account owner should hear about (published as a Spring event; the e-mail service sends
 * the alert): NEW_DEVICE_LOGIN, ACCOUNT_TEMP_LOCKED, PASSWORD_CHANGED, PASSWORD_RESET, TWO_FACTOR_ENABLED,
 * TWO_FACTOR_DISABLED.
 */
public record SecurityEvent(Long userId, String type, Map<String, Object> details) {
}
