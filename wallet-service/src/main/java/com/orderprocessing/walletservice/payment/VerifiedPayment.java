package com.orderprocessing.walletservice.payment;

import java.util.UUID;

public record VerifiedPayment(UUID intentId, long amount, String currency, String providerReference) {
}
