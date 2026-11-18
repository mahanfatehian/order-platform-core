package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.config.WalletServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "wallet-service", configuration = WalletServiceFeignConfig.class)
public interface WalletServiceFeignClient {
    @GetMapping("/api/wallets/internal/holds")
    List<OpenHoldResponse> openHolds(@RequestParam Instant createdBefore, @RequestParam int limit);

    @PostMapping("/api/wallets/internal/holds")
    WalletEntryResponse hold(@RequestBody WalletHoldRequest request);

    @PostMapping("/api/wallets/internal/holds/{holdId}/capture")
    WalletEntryResponse capture(@PathVariable UUID holdId, @RequestBody WalletKeyRequest request);

    @PostMapping("/api/wallets/internal/holds/{holdId}/release")
    WalletEntryResponse release(@PathVariable UUID holdId, @RequestBody WalletKeyRequest request);

    record OpenHoldResponse(UUID holdId, UUID orderId, long amount, Instant createdAt) {
    }

    record WalletHoldRequest(UUID userId, UUID orderId, long amount, String idempotencyKey) {
    }

    record WalletKeyRequest(String idempotencyKey) {
    }

    record WalletEntryResponse(UUID id, UUID walletId, String type, long amount, String currency, UUID holdId) {
    }
}
