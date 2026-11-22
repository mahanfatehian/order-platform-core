package com.orderprocessing.notificationservice.client;

import com.orderprocessing.notificationservice.config.UserInternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;
import java.util.UUID;

@FeignClient(name = "user-service", configuration = UserInternalFeignConfig.class)
public interface UserInternalClient {

    @GetMapping("/api/users/internal/{id}")
    Profile get(@PathVariable UUID id);

    record Profile(UUID id, String username, String email, boolean enabled, boolean accountNonLocked, Set<String> roles) {
    }
}
