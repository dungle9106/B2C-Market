package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Card number checks of the simulated card payment. */
class PaymentRulesTest {

    @Test
    void luhnChecksum() {
        assertTrue(PaymentService.luhn("4111111111111111"));
        assertTrue(PaymentService.luhn("5555555555554444"));
        assertFalse(PaymentService.luhn("4111111111111112"));
    }

    @Test
    void cardBrand() {
        assertEquals("VISA", PaymentService.cardBrand("4111111111111111"));
        assertEquals("MASTERCARD", PaymentService.cardBrand("5555555555554444"));
        assertEquals("MASTERCARD", PaymentService.cardBrand("2221000000000009"));
        assertEquals("JCB", PaymentService.cardBrand("3530111333300000"));
        assertNull(PaymentService.cardBrand("6011000990139424"));
    }
}
