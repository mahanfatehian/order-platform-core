package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.model.Wallet;
import com.orderprocessing.walletservice.repository.LedgerEntryRepository;
import com.orderprocessing.walletservice.repository.WalletRepository;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPaymentApplierTest {
    @Mock PaymentIntentRepository intents;
    @Mock WalletRepository wallets;
    @Mock LedgerEntryRepository entries;
    @Mock WalletLedger ledger;

    WalletPaymentApplier applier;

    @BeforeEach
    void setUp() {
        applier = new WalletPaymentApplier(intents, wallets, entries, ledger);
    }

    @Test
    void aVerifiedTopUpCreditsOnce() {
        UUID userId = UUID.randomUUID();
        PaymentIntent intent = intent(PaymentIntentType.WALLET_TOPUP, 2500);
        Wallet wallet = new Wallet();
        wallet.setId(intent.getWalletId());
        wallet.setUserId(userId);
        LedgerEntry credit = new LedgerEntry();
        credit.setId(UUID.randomUUID());
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(wallets.findById(intent.getWalletId())).thenReturn(Optional.of(wallet));
        when(ledger.credit(userId, 2500, "gateway top-up", "pay-1", "GATEWAY", "GATEWAY", intent.getId().toString()))
                .thenReturn(credit);

        LedgerEntry applied = applier.apply(new VerifiedPayment(intent.getId(), 2500, "IRR", "pay-1"));

        assertThat(applied.getId()).isEqualTo(credit.getId());
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.VERIFIED);
        verify(ledger).credit(userId, 2500, "gateway top-up", "pay-1", "GATEWAY", "GATEWAY", intent.getId().toString());
    }

    @Test
    void aSecondVerifyWithTheSameReferenceDoesNotCreditAgain() {
        PaymentIntent intent = intent(PaymentIntentType.WALLET_TOPUP, 2500);
        intent.setStatus(PaymentIntentStatus.VERIFIED);
        intent.setProviderReference("pay-1");
        UUID entryId = UUID.randomUUID();
        intent.setLedgerEntryId(entryId);
        LedgerEntry existing = new LedgerEntry();
        existing.setId(entryId);
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(entries.findById(entryId)).thenReturn(Optional.of(existing));

        LedgerEntry applied = applier.apply(new VerifiedPayment(intent.getId(), 2500, "IRR", "pay-1"));

        assertThat(applied.getId()).isEqualTo(entryId);
        verify(ledger, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anAmountMismatchPostsNothingAndRejectsTheIntent() {
        PaymentIntent intent = intent(PaymentIntentType.WALLET_TOPUP, 2500);
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));

        LedgerEntry applied = applier.apply(new VerifiedPayment(intent.getId(), 2499, "IRR", "pay-1"));

        assertThat(applied).isNull();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.REJECTED);
        verify(ledger, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static PaymentIntent intent(PaymentIntentType type, long amount) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setType(type);
        intent.setWalletId(UUID.randomUUID());
        intent.setAmount(amount);
        intent.setCurrency("IRR");
        intent.setStatus(PaymentIntentStatus.CREATED);
        intent.setIdempotencyKey(UUID.randomUUID().toString());
        return intent;
    }
}
