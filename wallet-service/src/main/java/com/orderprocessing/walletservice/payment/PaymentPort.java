package com.orderprocessing.walletservice.payment;

import java.util.UUID;

public interface PaymentPort {
    String start(UUID intentId);

    VerifiedPayment verify(UUID intentId);
}
