package com.orderprocessing.orderservice.tax;

import java.util.UUID;

public record TaxInvoiceView(UUID id, UUID orderId, long goodsMinor, int rateBps, long taxMinor, long chargedMinor) {
}
