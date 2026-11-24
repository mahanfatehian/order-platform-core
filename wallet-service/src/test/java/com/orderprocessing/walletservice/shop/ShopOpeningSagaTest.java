package com.orderprocessing.walletservice.shop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopOpeningSagaTest {
    @Test
    void stockAndBalanceEndWhereTheSetupLeftThem() {
        ShopOpeningSaga saga = new ShopOpeningSaga();
        saga.createProduct();
        saga.receiveStock(1);
        saga.credit(1000, "credit-1");
        assertThat(saga.stock()).isEqualTo(1);
        assertThat(saga.booked()).isEqualTo(1000);
        saga.buy(1000, "order-1");
        saga.deliver(1);
        saga.acceptReturn(1, 1000);
        assertThat(saga.stock()).isEqualTo(1);
        assertThat(saga.booked()).isEqualTo(1000);
        assertThat(saga.mails()).isEqualTo(1);
    }
}
