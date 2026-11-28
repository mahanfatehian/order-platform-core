package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;

public final class Receipts {
    private Receipts() {
    }

    public static String confirmed(OrderConfirmedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("Order ").append(event.getOrderId()).append('\n');
        if (event.getLines() != null) {
            for (OrderConfirmedEvent.Line line : event.getLines()) {
                body.append(line.getProductId()).append(' ').append(line.getQuantity()).append('\n');
            }
        }
        long charged = event.getCapturedMinor() == null ? 0L : event.getCapturedMinor();
        long tax = event.getTaxMinor() == null ? 0L : event.getTaxMinor();
        long goods = event.getGoodsMinor() == null ? charged - tax : event.getGoodsMinor();
        body.append("Goods: ").append(goods).append(" rials\n");
        body.append("Tax: ").append(tax).append(" rials\n");
        body.append("Total: ").append(charged).append(" rials\n");
        body.append("Ship to: ")
                .append(text(event.getShipRecipient())).append(", ")
                .append(text(event.getShipLine1())).append(", ")
                .append(text(event.getShipLine2())).append(", ")
                .append(text(event.getShipCity())).append(", ")
                .append(text(event.getShipRegion())).append(", ")
                .append(text(event.getShipPostalCode())).append(", ")
                .append(text(event.getShipPhone())).append(", ")
                .append(text(event.getShipCountry())).append('\n');
        body.append("Wallet capture: ").append(event.getWalletCaptureEntryId());
        return body.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
