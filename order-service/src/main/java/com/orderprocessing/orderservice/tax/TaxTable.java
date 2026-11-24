package com.orderprocessing.orderservice.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TaxTable {
    public static final String COUNTRY = "IR";
    public static final int MAX_BPS = 10_000;

    private final int rateBps;

    public TaxTable(int rateBps) {
        if (rateBps < 0 || rateBps > MAX_BPS) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 10000 basis points");
        }
        this.rateBps = rateBps;
    }

    public static TaxTable none() {
        return new TaxTable(0);
    }

    public int rateBps() {
        return rateBps;
    }

    public long taxMinor(long goodsMinor) {
        if (goodsMinor < 0) {
            throw new IllegalArgumentException("Tax is calculated on a non-negative goods amount");
        }
        return BigDecimal.valueOf(goodsMinor)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(MAX_BPS), 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
