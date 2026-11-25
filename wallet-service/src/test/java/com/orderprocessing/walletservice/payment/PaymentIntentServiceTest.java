package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.model.Wallet;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {
    @Mock PaymentIntentRepository intents;
    @Mock WalletRepository wallets;
    @Mock LedgerEntryRepository entries;
    @Mock WalletLedger ledger;

    @Test
    void aTopUpAndAnOrderPaymentCanBeOpenedOnce() {
        when(intents.findByIdempotencyKey("top-1")).thenReturn(Optional.empty());
        when(intents.findByIdempotencyKey("order-1")).thenReturn(Optional.empty());
        when(intents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentIntentService service = new PaymentIntentService(intents);
        UUID walletId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PaymentIntent topUp = service.open(PaymentIntentType.WALLET_TOPUP, walletId, null, 2500, "top-1");
        PaymentIntent checkout = service.open(PaymentIntentType.ORDER_CHECKOUT, walletId, orderId, 1100, "order-1");

        assertThat(topUp.getType()).isEqualTo(PaymentIntentType.WALLET_TOPUP);
        assertThat(topUp.getCurrency()).isEqualTo("IRR");
        assertThat(topUp.getOrderId()).isNull();
        assertThat(checkout.getType()).isEqualTo(PaymentIntentType.ORDER_CHECKOUT);
        assertThat(checkout.getOrderId()).isEqualTo(orderId);
        assertThat(checkout.getStatus()).isEqualTo(PaymentIntentStatus.CREATED);
    }

    @Test
    void anOrderPaymentWithoutAnOrderIsRejected() {
        PaymentIntentService service = new PaymentIntentService(intents);
        assertThatThrownBy(() -> service.open(PaymentIntentType.ORDER_CHECKOUT, UUID.randomUUID(), null, 100, "bad"))
                .isInstanceOf(WalletException.class);
        verify(intents, never()).save(any());
    }

    @Test
    void aVerifiedOrderPaymentDoesNotCreditTheWallet() {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setType(PaymentIntentType.ORDER_CHECKOUT);
        intent.setWalletId(UUID.randomUUID());
        intent.setAmount(1100);
        intent.setCurrency("IRR");
        intent.setStatus(PaymentIntentStatus.CREATED);
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        WalletPaymentApplier applier = new WalletPaymentApplier(intents, wallets, entries, ledger);

        LedgerEntry applied = applier.apply(new VerifiedPayment(intent.getId(), 1100, "IRR", "pay-1"));
        LedgerEntry replay = applier.apply(new VerifiedPayment(intent.getId(), 1100, "IRR", "pay-1"));

        assertThat(applied).isNull();
        assertThat(replay).isNull();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.VERIFIED);
        verify(ledger, never()).credit(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any(), any());
    }
}
