package com.marketplace.backend.entity;

public enum PaymentMethod {
    COD,            // thanh toan khi nhan hang
    E_WALLET,       // vi dien tu: MoMo / ZaloPay / VNPay (simulated gateway)
    CARD,           // the tin dung / ghi no (simulated gateway)
    BANK_TRANSFER,  // chuyen khoan ngan hang
    VNPAY,          // real gateway: the buyer pays on VNPay's page (ATM card, QR, international card)
    MOCK_ONLINE     // legacy value from the MVP, kept so old orders still load
}
