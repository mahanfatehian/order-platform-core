package com.orderprocessing.orderservice.money;

import com.orderprocessing.orderservice.exception.QuoteScaleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WalletMoney {
    public static final int QUOTE_SCALE = 2;

    private WalletMoney() {
    }

    public static long minorUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new QuoteScaleException("A wallet command needs a positive amount");
        }
        try {
            return amount.setScale(QUOTE_SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException ex) {
            throw new QuoteScaleException("Order total scale does not match the wallet minor unit");
        }
    }
}
