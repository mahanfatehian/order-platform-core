package com.orderprocessing.orderservice.client;

import java.util.UUID;

public interface WalletLedgerClient {
    HoldPlaced placeHold(UUID userId, UUID orderId, long amountMinor, String idempotencyKey, String correlationId);

    UUID captureHold(UUID holdId, String idempotencyKey, String correlationId);

    void releaseHold(UUID holdId, String idempotencyKey, String correlationId);

    record HoldPlaced(UUID holdId, long amountMinor) {
    }
}
