package com.orderprocessing.orderservice.tax;

import com.orderprocessing.orderservice.model.Order;
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
class TaxInvoiceIssuerTest {
    @Mock TaxInvoiceRepository invoices;

    @Test
    void theInvoiceCopiesTheOrderSnapshot() {
        Order order = order();
        when(invoices.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(invoices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TaxInvoice issued = new TaxInvoiceIssuer(invoices).issueIfAbsent(order);

        assertThat(issued.getGoodsMinor()).isEqualTo(1000L);
        assertThat(issued.getRateBps()).isEqualTo(1000);
        assertThat(issued.getTaxMinor()).isEqualTo(100L);
        assertThat(issued.getChargedMinor()).isEqualTo(1100L);
        assertThat(issued.getUserId()).isEqualTo(order.getUserId());
    }

    private static Order order() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setTaxRateBps(1000);
        order.setTaxAmountMinor(100L);
        order.setWalletAmountMinor(1100L);
        return order;
    }
}
