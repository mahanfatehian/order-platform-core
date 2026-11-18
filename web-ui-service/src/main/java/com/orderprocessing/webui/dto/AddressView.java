package com.orderprocessing.webui.dto;

import java.util.UUID;

public record AddressView(
        UUID id,
        UUID userId,
        String recipient,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String phone,
        String country,
        boolean defaultAddress
) {
    public String summary() {
        String second = line2 == null || line2.isBlank() ? "" : ", " + line2;
        return recipient + ", " + line1 + second + ", " + city + ", " + region + " " + postalCode;
    }
}
