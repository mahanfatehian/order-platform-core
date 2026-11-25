package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.model.Wallet;
import com.orderprocessing.walletservice.repository.LedgerEntryRepository;
import com.orderprocessing.walletservice.repository.WalletRepository;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletPaymentApplier {
    private final PaymentIntentRepository intents;
    private final WalletRepository wallets;
    private final LedgerEntryRepository entries;
    private final WalletLedger ledger;

    public WalletPaymentApplier(PaymentIntentRepository intents, WalletRepository wallets,
                                LedgerEntryRepository entries, WalletLedger ledger) {
        this.intents = intents;
        this.wallets = wallets;
        this.entries = entries;
        this.ledger = ledger;
    }

    @Transactional
    public LedgerEntry apply(VerifiedPayment payment) {
        PaymentIntent intent = intents.findById(payment.intentId())
                .orElseThrow(() -> WalletException.notFound("INTENT_NOT_FOUND", "Payment intent does not exist"));
        if (intent.getStatus() == PaymentIntentStatus.VERIFIED) {
            if (!payment.providerReference().equals(intent.getProviderReference())) {
                throw WalletException.conflict("PROVIDER_REFERENCE_CONFLICT",
                        "This intent was already verified with another reference");
            }
            if (intent.getLedgerEntryId() == null) {
                return null;
            }
            return entries.findById(intent.getLedgerEntryId()).orElseThrow();
        }
        if (intent.getAmount() != payment.amount() || !intent.getCurrency().equals(payment.currency())) {
            intent.setStatus(PaymentIntentStatus.REJECTED);
            intents.save(intent);
            return null;
        }
        if (intent.getType() != PaymentIntentType.WALLET_TOPUP) {
            intent.setStatus(PaymentIntentStatus.VERIFIED);
            intent.setProviderReference(payment.providerReference());
            intents.save(intent);
            return null;
        }
        Wallet wallet = wallets.findById(intent.getWalletId())
                .orElseThrow(() -> WalletException.notFound("WALLET_NOT_FOUND", "No wallet for this intent"));
        LedgerEntry entry = ledger.credit(wallet.getUserId(), intent.getAmount(), "gateway top-up",
                payment.providerReference(), "GATEWAY", "GATEWAY", payment.intentId().toString());
        intent.setStatus(PaymentIntentStatus.VERIFIED);
        intent.setProviderReference(payment.providerReference());
        intent.setLedgerEntryId(entry.getId());
        intents.save(intent);
        return entry;
    }
}
