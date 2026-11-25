package com.orderprocessing.walletservice.payment;

import com.orderprocessing.walletservice.exception.WalletException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentIntentService {
    private final PaymentIntentRepository intents;

    public PaymentIntentService(PaymentIntentRepository intents) {
        this.intents = intents;
    }

    @Transactional
    public PaymentIntent open(PaymentIntentType type, UUID walletId, UUID orderId, long amount, String idempotencyKey) {
        if (type == null || walletId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw WalletException.badRequest("INTENT_INVALID", "An intent needs a type, a wallet, and a key");
        }
        if (amount <= 0) {
            throw WalletException.badRequest("INTENT_AMOUNT", "An intent amount must be positive");
        }
        if (type == PaymentIntentType.ORDER_CHECKOUT && orderId == null) {
            throw WalletException.badRequest("INTENT_ORDER", "An order payment needs an order id");
        }
        if (type == PaymentIntentType.WALLET_TOPUP && orderId != null) {
            throw WalletException.badRequest("INTENT_ORDER", "A top-up does not belong to an order");
        }
        return intents.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            PaymentIntent intent = new PaymentIntent();
            intent.setId(UUID.randomUUID());
            intent.setType(type);
            intent.setWalletId(walletId);
            intent.setOrderId(orderId);
            intent.setAmount(amount);
            intent.setCurrency("IRR");
            intent.setStatus(PaymentIntentStatus.CREATED);
            intent.setIdempotencyKey(idempotencyKey);
            intent.setCreatedAt(Instant.now());
            return intents.save(intent);
        });
    }
}
