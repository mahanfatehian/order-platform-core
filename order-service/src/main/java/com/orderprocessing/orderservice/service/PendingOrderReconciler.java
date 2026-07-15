package com.orderprocessing.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class PendingOrderReconciler {
    private final OrderService orderService;
    private final Duration timeout;
    private final int batchSize;

    public PendingOrderReconciler(OrderService orderService,
                                  @Value("${saga.pending-timeout:PT10M}") Duration timeout,
                                  @Value("${saga.reconciliation.batch-size:50}") int batchSize) {
        this.orderService = orderService;
        this.timeout = timeout;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${saga.reconciliation.interval:60000}")
    public void reconcile() {
        int reconciled = orderService.failStalePendingOrders(Instant.now().minus(timeout), batchSize);
        if (reconciled > 0) {
            log.warn("Reconciled {} orders that exceeded the {} pending timeout", reconciled, timeout);
        }
    }
}
