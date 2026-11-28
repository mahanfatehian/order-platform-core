package com.orderprocessing.webui.shop;

import com.orderprocessing.webui.dto.OrderItemView;
import com.orderprocessing.webui.dto.OrderView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerInvoiceTest {
    @Test
    void theInvoiceCopiesTheOrderSnapshotAndDoesNotMultiplyByOneHundred() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        OrderView order = new OrderView(orderId, UUID.randomUUID(), "CONFIRMED", new BigDecimal("10.00"), null,
                List.of(new OrderItemView(UUID.randomUUID(), UUID.randomUUID(), "Lamp", new BigDecimal("10.00"), 1,
                        new BigDecimal("10.00"))),
                Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-01-02T03:04:05Z"), true,
                null, holdId, 1100L, "HELD",
                "Ada", "12 Enghelab", "Unit 4", "Tehran", "Tehran", "1136912345", "02112345678", "IR",
                1000, 100L);

        CustomerInvoice invoice = CustomerInvoice.from(order);
        String text = invoice.text();

        assertThat(invoice.goodsMinor()).isEqualTo(1000L);
        assertThat(invoice.taxMinor()).isEqualTo(100L);
        assertThat(invoice.chargedMinor()).isEqualTo(1100L);
        assertThat(invoice.lines()).containsExactly("Lamp x 1");
        assertThat(text).contains("Goods: 1000 rials", "Tax: 100 rials", "Charged: 1100 rials");
        assertThat(text).contains("Ada", "12 Enghelab", "IR");
        assertThat(text).doesNotContain("110000");
        assertThat(text.toLowerCase()).doesNotContain("vat");
        assertThat(text).contains("does not name a tax authority");
    }

    @Test
    void aMissingOrderIsRejected() {
        assertThatThrownBy(() -> CustomerInvoice.from(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
