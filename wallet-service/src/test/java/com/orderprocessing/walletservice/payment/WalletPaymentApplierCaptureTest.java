package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.repository.LedgerEntryRepository;
import com.orderprocessing.walletservice.repository.WalletRepository;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPaymentApplierCaptureTest {
    @Mock PaymentIntentRepository intents;
    @Mock WalletRepository wallets;
    @Mock LedgerEntryRepository entries;
    @Mock WalletLedger ledger;
    @Mock OrderPaymentCapture orderPayments;

    @Test
    void aVerifiedOrderPaymentCapturesThroughTheLedgerCollaborator() {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setType(PaymentIntentType.ORDER_CHECKOUT);
        intent.setOrderId(UUID.randomUUID());
        intent.setAmount(1100);
        intent.setCurrency("IRR");
        intent.setStatus(PaymentIntentStatus.CREATED);
        LedgerEntry captured = new LedgerEntry();
        captured.setId(UUID.randomUUID());
        VerifiedPayment payment = new VerifiedPayment(intent.getId(), 1100, "IRR", "pay-1");
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(orderPayments.capture(payment, intent)).thenReturn(captured);
        WalletPaymentApplier applier = new WalletPaymentApplier(intents, wallets, entries, ledger);
        applier.setOrderPaymentCapture(orderPayments);

        LedgerEntry applied = applier.apply(payment);

        assertThat(applied.getId()).isEqualTo(captured.getId());
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.VERIFIED);
        assertThat(intent.getLedgerEntryId()).isEqualTo(captured.getId());
        verify(orderPayments).capture(payment, intent);
    }
}
