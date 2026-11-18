package com.orderprocessing.orderservice.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalletLedgerClient {
    HoldPlaced placeHold(UUID userId, UUID orderId, long amountMinor, String idempotencyKey, String correlationId);

    UUID captureHold(UUID holdId, String idempotencyKey, String correlationId);

    UUID refundCapture(UUID captureEntryId, String idempotencyKey, String correlationId);

    void releaseHold(UUID holdId, String idempotencyKey, String correlationId);

    List<OpenHold> listOpenHolds(Instant createdBefore, int limit);

    record HoldPlaced(UUID holdId, long amountMinor) {
    }

    record OpenHold(UUID holdId, UUID orderId, long amount, Instant createdAt) {
    }
}
