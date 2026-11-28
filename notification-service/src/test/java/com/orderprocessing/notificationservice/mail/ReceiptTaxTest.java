package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTaxTest {
    @Test
    void theReceiptStatesTheStoredTaxAndDoesNotMultiplyByOneHundred() {
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        event.setOrderId(UUID.randomUUID());
        event.setGoodsMinor(1000L);
        event.setRateBps(1000);
        event.setTaxMinor(100L);
        event.setCapturedMinor(1100L);

        String body = Receipts.confirmed(event);

        assertThat(body).contains("Goods: 1000 rials");
        assertThat(body).contains("Tax: 100 rials");
        assertThat(body).contains("Total: 1100 rials");
        assertThat(body).doesNotContain("110000");
        assertThat(body.toLowerCase()).doesNotContain("vat");
    }
}
