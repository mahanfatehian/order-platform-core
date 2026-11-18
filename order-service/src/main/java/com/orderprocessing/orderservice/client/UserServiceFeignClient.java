package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.config.UserServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-service", configuration = UserServiceFeignConfig.class)
public interface UserServiceFeignClient {

    @GetMapping("/api/users/internal/addresses/{id}")
    AddressSnapshot address(@PathVariable UUID id, @RequestParam UUID userId);

    record AddressSnapshot(
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
}
