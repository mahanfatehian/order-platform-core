package com.orderprocessing.walletservice.controller;

import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets/me")
public class CustomerWalletController {
    private final WalletLedger ledger;

    public CustomerWalletController(WalletLedger ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/balance")
    public WalletLedger.Balance balance(@AuthenticationPrincipal Jwt jwt) {
        return ledger.balance(userId(jwt));
    }

    @GetMapping("/entries")
    public List<EntryView> entries(@AuthenticationPrincipal Jwt jwt) {
        return ledger.entries(userId(jwt)).stream().map(EntryView::from).toList();
    }

    private UUID userId(Jwt jwt) {
        String claim = jwt == null ? null : jwt.getClaimAsString("userId");
        if (claim == null || claim.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("The token has no wallet owner");
        }
        return UUID.fromString(claim);
    }

    public record EntryView(UUID id, String type, long amount, String currency, String reason, String actorRole, Instant createdAt) {
        static EntryView from(LedgerEntry entry) {
            return new EntryView(entry.getId(), entry.getEntryType().name(), entry.getAmount(), entry.getCurrency(),
                    entry.getReason(), entry.getActorRole(), entry.getCreatedAt());
        }
    }
}
