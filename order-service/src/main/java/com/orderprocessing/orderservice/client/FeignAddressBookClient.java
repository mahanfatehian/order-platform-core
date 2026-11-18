package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FeignAddressBookClient implements AddressBookClient {

    private final UserServiceFeignClient client;

    public FeignAddressBookClient(UserServiceFeignClient client) {
        this.client = client;
    }

    @Override
    public ShipToAddress findOwned(UUID userId, UUID addressId) {
        try {
            UserServiceFeignClient.AddressSnapshot snapshot = client.address(addressId, userId);
            if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
                throw new ResourceNotFoundException("Address not found");
            }
            return new ShipToAddress(
                    snapshot.recipient(),
                    snapshot.line1(),
                    snapshot.line2(),
                    snapshot.city(),
                    snapshot.region(),
                    snapshot.postalCode(),
                    snapshot.phone(),
                    snapshot.country());
        } catch (FeignException.NotFound exception) {
            throw new ResourceNotFoundException("Address not found");
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Address book is temporarily unavailable", exception);
        }
    }
}
