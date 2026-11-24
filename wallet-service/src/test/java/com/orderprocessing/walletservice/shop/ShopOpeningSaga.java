package com.orderprocessing.walletservice.shop;

public class ShopOpeningSaga {
    private int stock;
    private long booked;
    private long captured;
    private int mails;
    private boolean returned;

    public int stock() {
        return stock;
    }

    public long booked() {
        return booked;
    }

    public int mails() {
        return mails;
    }

    public void createProduct() {
    }

    public void receiveStock(int quantity) {
        stock += quantity;
    }
}
