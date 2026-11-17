package com.orderprocessing.walletservice.controller;

import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.service.WalletLedger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets/internal")
public class InternalWalletController {

    private final WalletLedger ledger;

    public InternalWalletController(WalletLedger ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/credits")
    public EntryResponse credit(@Valid @RequestBody CreditRequest request,
                                @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntryResponse.from(ledger.credit(request.userId(), request.amount(), request.reason(),
                request.idempotencyKey(), request.actorId(), request.actorRole(), correlationId));
    }

    @PostMapping("/holds")
    public EntryResponse hold(@Valid @RequestBody HoldRequest request,
                              @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntryResponse.from(ledger.placeHold(request.userId(), request.orderId(), request.amount(),
                request.idempotencyKey(), correlationId));
    }

    @PostMapping("/holds/{holdId}/capture")
    public EntryResponse capture(@PathVariable UUID holdId, @Valid @RequestBody KeyRequest request,
                                 @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntryResponse.from(ledger.captureHold(holdId, request.idempotencyKey(), correlationId));
    }

    @PostMapping("/holds/{holdId}/release")
    public EntryResponse release(@PathVariable UUID holdId, @Valid @RequestBody KeyRequest request,
                                 @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntryResponse.from(ledger.releaseHold(holdId, request.idempotencyKey(), correlationId));
    }

    @PostMapping("/captures/{captureEntryId}/refund")
    public EntryResponse refund(@PathVariable UUID captureEntryId, @Valid @RequestBody KeyRequest request,
                                @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntryResponse.from(ledger.refundCapture(captureEntryId, request.idempotencyKey(), correlationId));
    }

    @GetMapping("/users/{userId}/balance")
    public WalletLedger.Balance balance(@PathVariable UUID userId) {
        return ledger.balance(userId);
    }

    public record CreditRequest(
            @NotNull UUID userId,
            @Positive long amount,
            @NotBlank String reason,
            @NotBlank String idempotencyKey,
            @NotBlank String actorId,
            @NotBlank String actorRole
    ) {
    }

    public record HoldRequest(
            @NotNull UUID userId,
            @NotNull UUID orderId,
            @Positive long amount,
            @NotBlank String idempotencyKey
    ) {
    }

    public record KeyRequest(@NotBlank String idempotencyKey) {
    }

    public record EntryResponse(UUID id, UUID walletId, String type, long amount, String currency, UUID holdId) {
        static EntryResponse from(LedgerEntry entry) {
            return new EntryResponse(entry.getId(), entry.getWalletId(), entry.getEntryType().name(),
                    entry.getAmount(), entry.getCurrency(), entry.getHoldId());
        }
    }
}
