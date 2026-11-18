package com.orderprocessing.userservice.dto;

import java.util.UUID;

public record AddressResponse(
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
}
