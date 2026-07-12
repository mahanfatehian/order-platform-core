package com.orderprocessing.authservice.client;

import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.client.dto.InternalUserStateResponse;
import com.orderprocessing.authservice.config.FeignInternalAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "user-service",
        configuration = FeignInternalAuthConfig.class
)
public interface UserServiceClient {
    @PostMapping("/api/users/internal/authenticate")
    InternalAuthenticatedUserResponse authenticate(@RequestBody InternalAuthenticateRequest request);

    @GetMapping("/api/users/internal/{id}")
    InternalUserStateResponse getCurrentState(@PathVariable("id") UUID id);
}
