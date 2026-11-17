package com.orderprocessing.webui.dto;

import java.util.UUID;

public record WalletBalanceView(
        UUID userId,
        String currency,
        long booked,
        long available,
        long openHolds
) {
}
