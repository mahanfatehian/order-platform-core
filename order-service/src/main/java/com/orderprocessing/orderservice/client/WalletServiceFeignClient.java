package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.config.WalletServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "wallet-service", configuration = WalletServiceFeignConfig.class)
public interface WalletServiceFeignClient {
    @PostMapping("/api/wallets/internal/holds")
    WalletEntryResponse hold(@RequestBody WalletHoldRequest request);

    @PostMapping("/api/wallets/internal/holds/{holdId}/capture")
    WalletEntryResponse capture(@PathVariable UUID holdId, @RequestBody WalletKeyRequest request);

    @PostMapping("/api/wallets/internal/holds/{holdId}/release")
    WalletEntryResponse release(@PathVariable UUID holdId, @RequestBody WalletKeyRequest request);

    record WalletHoldRequest(UUID userId, UUID orderId, long amount, String idempotencyKey) {
    }

    record WalletKeyRequest(String idempotencyKey) {
    }

    record WalletEntryResponse(UUID id, UUID walletId, String type, long amount, String currency, UUID holdId) {
    }
}
