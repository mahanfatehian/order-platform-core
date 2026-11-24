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

    public void credit(long amount, String key) {
        if (key.equals(creditedKey)) {
            return;
        }
        booked += amount;
        creditedKey = key;
    }

    private String creditedKey;
    private String capturedKey;

    public void buy(long amount, String key) {
        if (key.equals(capturedKey)) {
            return;
        }
        if (booked < amount) {
            throw new IllegalStateException("short balance");
        }
        booked -= amount;
        captured += amount;
        capturedKey = key;
        mails += 1;
    }

    public void deliver(int quantity) {
        stock -= quantity;
    }
}
