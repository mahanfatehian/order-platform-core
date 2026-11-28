package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.Hold;
import com.orderprocessing.walletservice.model.HoldStatus;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.repository.HoldRepository;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderPaymentCapture {
    private final HoldRepository holds;
    private final WalletLedger ledger;

    public OrderPaymentCapture(HoldRepository holds, WalletLedger ledger) {
        this.holds = holds;
        this.ledger = ledger;
    }

    @Transactional
    public LedgerEntry capture(VerifiedPayment payment, PaymentIntent intent) {
        refuse(payment, intent);
        Hold hold = holds.findFirstByOrderId(intent.getOrderId())
                .orElseThrow(() -> WalletException.notFound("HOLD_NOT_FOUND", "No hold for this order"));
        if (hold.getAmount() != payment.amount() || !"IRR".equals(payment.currency())) {
            throw WalletException.conflict("CAPTURE_AMOUNT", "The verified amount does not match the open hold");
        }
        if (hold.getStatus() != HoldStatus.OPEN && hold.getStatus() != HoldStatus.CAPTURED) {
            throw WalletException.conflict("HOLD_NOT_OPEN", "Only an open hold can be captured");
        }
        return ledger.captureHold(hold.getId(), payment.providerReference(), payment.intentId().toString());
    }

    public void refuse(VerifiedPayment payment, PaymentIntent intent) {
        if (payment == null || intent == null) {
            throw WalletException.badRequest("CAPTURE_INVALID", "A capture needs a verified payment and an intent");
        }
        if (!payment.intentId().equals(intent.getId())) {
            throw WalletException.conflict("INTENT_MISMATCH", "The verified payment does not match the intent");
        }
        if (intent.getType() != PaymentIntentType.ORDER_CHECKOUT) {
            throw WalletException.conflict("NOT_AN_ORDER_PAYMENT", "A top-up is not an order capture");
        }
        if (intent.getOrderId() == null) {
            throw WalletException.badRequest("INTENT_ORDER", "An order payment needs an order id");
        }
        if (payment.amount() != intent.getAmount() || !payment.currency().equals(intent.getCurrency())) {
            throw WalletException.conflict("CAPTURE_AMOUNT", "The verified amount does not match the intent");
        }
    }

    public UUID orderId(PaymentIntent intent) {
        return intent.getOrderId();
    }
}
