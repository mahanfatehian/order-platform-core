package com.orderprocessing.orderservice.carrier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierHandoffServiceTest {
    @Mock CarrierHandoffRepository handoffs;

    @Test
    void aHandoffStoresTheReferenceTheHumanTyped() {
        UUID orderId = UUID.randomUUID();
        when(handoffs.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(handoffs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarrierHandoff stored = new CarrierHandoffService(handoffs).record(orderId, "TRACK-1");

        assertThat(stored.getTrackingReference()).isEqualTo("TRACK-1");
        assertThat(stored.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void aSecondRecordDoesNotInsertAgain() {
        UUID orderId = UUID.randomUUID();
        CarrierHandoff first = new CarrierHandoff();
        first.setOrderId(orderId);
        first.setTrackingReference("TRACK-1");
        when(handoffs.findByOrderId(orderId)).thenReturn(Optional.of(first));

        CarrierHandoff again = new CarrierHandoffService(handoffs).record(orderId, "TRACK-1");

        assertThat(again.getTrackingReference()).isEqualTo("TRACK-1");
        verify(handoffs, never()).save(any());
    }
}
