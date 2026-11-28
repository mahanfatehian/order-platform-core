package com.orderprocessing.webui.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class CheckoutCharge {
    public static final int MAX_BPS = 10_000;

    private final long goodsMinor;
    private final int rateBps;
    private final long taxMinor;
    private final long chargedMinor;

    private CheckoutCharge(long goodsMinor, int rateBps, long taxMinor, long chargedMinor) {
        this.goodsMinor = goodsMinor;
        this.rateBps = rateBps;
        this.taxMinor = taxMinor;
        this.chargedMinor = chargedMinor;
    }

    public static CheckoutCharge quote(BigDecimal goods, int rateBps) {
        if (rateBps < 0 || rateBps > MAX_BPS) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 10000 basis points");
        }
        long goodsMinor = WalletQuote.minorUnits(goods);
        if (goodsMinor < 0) {
            throw new IllegalArgumentException("Checkout goods must use two decimal places");
        }
        long taxMinor = taxOf(goodsMinor, rateBps);
        return new CheckoutCharge(goodsMinor, rateBps, taxMinor, goodsMinor + taxMinor);
    }

    public static long taxOf(long goodsMinor, int rateBps) {
        if (goodsMinor < 0 || rateBps < 0 || rateBps > MAX_BPS) {
            throw new IllegalArgumentException("Tax inputs are out of range");
        }
        return BigDecimal.valueOf(goodsMinor)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(MAX_BPS), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    public long goodsMinor() {
        return goodsMinor;
    }

    public int rateBps() {
        return rateBps;
    }

    public long taxMinor() {
        return taxMinor;
    }

    public long chargedMinor() {
        return chargedMinor;
    }

    public boolean canSpend(Long available) {
        return available != null && chargedMinor > 0 && available >= chargedMinor;
    }

    public String refusal(Long available) {
        if (chargedMinor <= 0) {
            return "The quoted charge is not positive";
        }
        if (available == null) {
            return "This account has no wallet yet";
        }
        if (available < chargedMinor) {
            return "This balance cannot cover the goods and the tax";
        }
        return null;
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("Goods: " + goodsMinor + " rials");
        lines.add("Rate: " + rateBps + " basis points");
        lines.add("Tax: " + taxMinor + " rials");
        lines.add("Charged: " + chargedMinor + " rials");
        return List.copyOf(lines);
    }
}
