package com.orderprocessing.walletservice.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByProviderReference(String providerReference);

    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
}
