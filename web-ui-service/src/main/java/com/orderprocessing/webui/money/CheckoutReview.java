package com.orderprocessing.webui.money;

import com.orderprocessing.webui.dto.WalletBalanceView;

public final class CheckoutReview {
    private final CheckoutCharge charge;
    private final Long available;
    private final boolean ready;

    public CheckoutReview(CheckoutCharge charge, WalletBalanceView wallet, boolean cartReady) {
        this.charge = charge;
        this.available = wallet == null ? null : wallet.available();
        this.ready = cartReady && charge.canSpend(available);
    }

    public CheckoutCharge charge() {
        return charge;
    }

    public Long available() {
        return available;
    }

    public boolean shortBalance() {
        return !charge.canSpend(available);
    }

    public boolean canPlace() {
        return ready;
    }

    public String notice() {
        if (charge.refusal(available) != null) {
            return charge.refusal(available);
        }
        if (!ready) {
            return "Availability changed. Review the cart before ordering.";
        }
        return "The wallet hold will be the charged amount, goods plus tax.";
    }
}
