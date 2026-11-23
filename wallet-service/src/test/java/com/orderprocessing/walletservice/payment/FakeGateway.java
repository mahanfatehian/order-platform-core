package com.orderprocessing.walletservice.payment;

import java.util.UUID;

public class FakeGateway implements PaymentPort {
    private VerifiedPayment configured;

    public void configure(VerifiedPayment configured) {
        this.configured = configured;
    }

    @Override
    public String start(UUID intentId) {
        return "fake:" + intentId;
    }

    @Override
    public VerifiedPayment verify(UUID intentId) {
        if (configured == null || !intentId.equals(configured.intentId())) {
            return null;
        }
        return configured;
    }
}
