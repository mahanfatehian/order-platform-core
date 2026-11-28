package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.Hold;
import com.orderprocessing.walletservice.model.HoldStatus;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.repository.HoldRepository;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentCaptureTest {
    @Mock HoldRepository holds;
    @Mock WalletLedger ledger;

    OrderPaymentCapture capture;
    UUID orderId;
    UUID holdId;
    UUID intentId;

    @BeforeEach
    void setUp() {
        capture = new OrderPaymentCapture(holds, ledger);
        orderId = UUID.randomUUID();
        holdId = UUID.randomUUID();
        intentId = UUID.randomUUID();
    }

    @Test
    void aMatchingVerifiedPaymentCapturesTheHoldOnce() {
        Hold hold = hold(1100, HoldStatus.OPEN);
        LedgerEntry entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        when(holds.findFirstByOrderId(orderId)).thenReturn(Optional.of(hold));
        when(ledger.captureHold(holdId, "pay-1", intentId.toString())).thenReturn(entry);

        LedgerEntry first = capture.capture(payment(1100), intent(1100));
        when(ledger.captureHold(holdId, "pay-1", intentId.toString())).thenReturn(entry);
        LedgerEntry second = capture.capture(payment(1100), intent(1100));

        assertThat(first.getId()).isEqualTo(entry.getId());
        assertThat(second.getId()).isEqualTo(entry.getId());
        verify(ledger, org.mockito.Mockito.times(2)).captureHold(holdId, "pay-1", intentId.toString());
    }

    @Test
    void aMismatchedHoldAmountDoesNotCapture() {
        when(holds.findFirstByOrderId(orderId)).thenReturn(Optional.of(hold(1100, HoldStatus.OPEN)));
        assertThatThrownBy(() -> capture.capture(payment(1000), intent(1000)))
                .isInstanceOf(WalletException.class);
        verify(ledger, never()).captureHold(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aTopUpIsNotAnOrderCapture() {
        PaymentIntent topUp = intent(1100);
        topUp.setType(PaymentIntentType.WALLET_TOPUP);
        topUp.setOrderId(null);
        assertThatThrownBy(() -> capture.capture(payment(1100), topUp)).isInstanceOf(WalletException.class);
        verify(ledger, never()).captureHold(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aReleasedHoldIsNotCaptured() {
        when(holds.findFirstByOrderId(orderId)).thenReturn(Optional.of(hold(1100, HoldStatus.RELEASED)));
        assertThatThrownBy(() -> capture.capture(payment(1100), intent(1100))).isInstanceOf(WalletException.class);
        verify(ledger, never()).captureHold(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private VerifiedPayment payment(long amount) {
        return new VerifiedPayment(intentId, amount, "IRR", "pay-1");
    }

    private PaymentIntent intent(long amount) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(intentId);
        intent.setType(PaymentIntentType.ORDER_CHECKOUT);
        intent.setOrderId(orderId);
        intent.setAmount(amount);
        intent.setCurrency("IRR");
        intent.setStatus(PaymentIntentStatus.VERIFIED);
        return intent;
    }

    private Hold hold(long amount, HoldStatus status) {
        Hold hold = new Hold();
        hold.setId(holdId);
        hold.setOrderId(orderId);
        hold.setAmount(amount);
        hold.setStatus(status);
        return hold;
    }
}
