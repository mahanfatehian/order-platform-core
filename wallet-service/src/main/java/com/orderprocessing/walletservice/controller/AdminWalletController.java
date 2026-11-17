package com.orderprocessing.walletservice.controller;

import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.service.WalletLedger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final WalletLedger ledger;

    public AdminWalletController(WalletLedger ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/{userId}/credits")
    public EntryView credit(@PathVariable UUID userId,
                                                         @Valid @RequestBody CreditRequest request,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        LedgerEntry entry = ledger.credit(userId, request.amount(), request.reason(), request.idempotencyKey(),
                jwt.getClaimAsString("userId"), "ADMIN", correlationId);
        return EntryView.from(entry);
    }

    @GetMapping("/{userId}/entries")
    public List<EntryView> entries(@PathVariable UUID userId) {
        return ledger.entries(userId).stream().map(EntryView::from).toList();
    }

    @GetMapping("/{userId}/balance")
    public WalletLedger.Balance balance(@PathVariable UUID userId) {
        return ledger.balance(userId);
    }

    public record CreditRequest(@Positive long amount, @NotBlank String reason, @NotBlank String idempotencyKey) {
    }

    public record EntryView(UUID id, String type, long amount, String currency, String reason, String actorRole, Instant createdAt) {
        static EntryView from(LedgerEntry entry) {
            return new EntryView(entry.getId(), entry.getEntryType().name(), entry.getAmount(), entry.getCurrency(),
                    entry.getReason(), entry.getActorRole(), entry.getCreatedAt());
        }
    }
}
