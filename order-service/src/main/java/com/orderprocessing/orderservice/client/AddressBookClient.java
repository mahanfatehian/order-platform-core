package com.orderprocessing.orderservice.client;

import java.util.UUID;

public interface AddressBookClient {

    ShipToAddress findOwned(UUID userId, UUID addressId);

    record ShipToAddress(
            String recipient,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String phone,
            String country
    ) {
    }
}
