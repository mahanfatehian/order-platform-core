package com.orderprocessing.webui.shop;

import com.orderprocessing.webui.dto.OrderItemView;
import com.orderprocessing.webui.dto.OrderView;

import java.util.ArrayList;
import java.util.List;

public final class CustomerInvoice {
    private final String orderId;
    private final String status;
    private final List<String> lines;
    private final long goodsMinor;
    private final int rateBps;
    private final long taxMinor;
    private final long chargedMinor;
    private final String shipTo;
    private final String captureId;

    private CustomerInvoice(String orderId, String status, List<String> lines, long goodsMinor, int rateBps,
                            long taxMinor, long chargedMinor, String shipTo, String captureId) {
        this.orderId = orderId;
        this.status = status;
        this.lines = lines;
        this.goodsMinor = goodsMinor;
        this.rateBps = rateBps;
        this.taxMinor = taxMinor;
        this.chargedMinor = chargedMinor;
        this.shipTo = shipTo;
        this.captureId = captureId;
    }

    public static CustomerInvoice from(OrderView order) {
        if (order == null || order.id() == null) {
            throw new IllegalArgumentException("An invoice needs an order");
        }
        long tax = order.taxAmountMinor() == null ? 0L : order.taxAmountMinor();
        long charged = order.walletAmountMinor() == null ? tax : order.walletAmountMinor();
        long goods = Math.max(0L, charged - tax);
        int rate = order.taxRateBps() == null ? 0 : order.taxRateBps();
        List<String> lines = new ArrayList<>();
        for (OrderItemView item : order.items()) {
            lines.add(item.productName() + " x " + item.quantity());
        }
        return new CustomerInvoice(order.id().toString(), order.status(), List.copyOf(lines), goods, rate, tax,
                charged, shipTo(order), order.walletHoldId() == null ? "" : order.walletHoldId().toString());
    }

    public String text() {
        StringBuilder body = new StringBuilder();
        body.append("Invoice\n");
        body.append("Order ").append(orderId).append('\n');
        body.append("Status ").append(status).append('\n');
        for (String line : lines) {
            body.append(line).append('\n');
        }
        body.append("Goods: ").append(goodsMinor).append(" rials\n");
        body.append("Rate: ").append(rateBps).append(" basis points\n");
        body.append("Tax: ").append(taxMinor).append(" rials\n");
        body.append("Charged: ").append(chargedMinor).append(" rials\n");
        body.append("Ship to: ").append(shipTo).append('\n');
        body.append("Wallet hold: ").append(captureId).append('\n');
        body.append("This page does not name a tax authority.\n");
        return body.toString();
    }

    public String orderId() {
        return orderId;
    }

    public List<String> lines() {
        return lines;
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

    public String shipTo() {
        return shipTo;
    }

    private static String shipTo(OrderView order) {
        return text(order.shipRecipient()) + ", " + text(order.shipLine1()) + ", " + text(order.shipLine2()) + ", "
                + text(order.shipCity()) + ", " + text(order.shipRegion()) + ", " + text(order.shipPostalCode())
                + ", " + text(order.shipPhone()) + ", " + text(order.shipCountry());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
