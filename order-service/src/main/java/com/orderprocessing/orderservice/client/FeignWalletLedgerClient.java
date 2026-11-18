package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.exception.InsufficientWalletBalanceException;
import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FeignWalletLedgerClient implements WalletLedgerClient {
    private final WalletServiceFeignClient client;

    public FeignWalletLedgerClient(WalletServiceFeignClient client) {
        this.client = client;
    }

    @Override
    public HoldPlaced placeHold(UUID userId, UUID orderId, long amountMinor, String idempotencyKey, String correlationId) {
        try {
            WalletServiceFeignClient.WalletEntryResponse response = client.hold(
                    new WalletServiceFeignClient.WalletHoldRequest(userId, orderId, amountMinor, idempotencyKey));
            if (response == null || response.holdId() == null) {
                throw new ServiceUnavailableException("Wallet hold response was invalid");
            }
            return new HoldPlaced(response.holdId(), response.amount());
        } catch (FeignException.Conflict exception) {
            throw mappedConflict(exception);
        } catch (FeignException.NotFound exception) {
            throw new InsufficientWalletBalanceException("WALLET_NOT_FOUND", "This account has no wallet");
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Wallet is temporarily unavailable", exception);
        }
    }

    @Override
    public UUID captureHold(UUID holdId, String idempotencyKey, String correlationId) {
        try {
            WalletServiceFeignClient.WalletEntryResponse response = client.capture(holdId,
                    new WalletServiceFeignClient.WalletKeyRequest(idempotencyKey));
            if (response == null || response.id() == null) {
                throw new ServiceUnavailableException("Wallet capture response was invalid");
            }
            return response.id();
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Wallet capture failed", exception);
        }
    }

    @Override
    public void releaseHold(UUID holdId, String idempotencyKey, String correlationId) {
        try {
            client.release(holdId, new WalletServiceFeignClient.WalletKeyRequest(idempotencyKey));
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Wallet release failed", exception);
        }
    }

    @Override
    public List<WalletLedgerClient.OpenHold> listOpenHolds(Instant createdBefore, int limit) {
        try {
            List<WalletServiceFeignClient.OpenHoldResponse> response = client.openHolds(createdBefore, limit);
            if (response == null) {
                return List.of();
            }
            return response.stream()
                    .map(hold -> new WalletLedgerClient.OpenHold(hold.holdId(), hold.orderId(), hold.amount(), hold.createdAt()))
                    .toList();
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Wallet hold scan failed", exception);
        }
    }

    private InsufficientWalletBalanceException mappedConflict(FeignException exception) {
        String body = exception.contentUTF8();
        if (body != null && body.contains("WALLET_NOT_FOUND")) {
            return new InsufficientWalletBalanceException("WALLET_NOT_FOUND", "This account has no wallet");
        }
        return new InsufficientWalletBalanceException("INSUFFICIENT_FUNDS", "Available wallet balance is short");
    }
}
