package com.orderprocessing.webui.money;

public final class WalletQuote {
    public static final int QUOTE_SCALE = 2;

    private WalletQuote() {
    }

    public static long minorUnits(java.math.BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        try {
            return amount.setScale(QUOTE_SCALE, java.math.RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    public static boolean canSpend(java.math.BigDecimal total, Long available) {
        long minor = minorUnits(total);
        return available != null && minor > 0 && available >= minor;
    }
}
