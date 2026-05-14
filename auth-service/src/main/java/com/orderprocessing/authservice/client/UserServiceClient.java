package com.orderprocessing.authservice.client;

import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.config.FeignInternalAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "user-service",
        url = "${services.user-service.url}",
        configuration = FeignInternalAuthConfig.class
)
public interface UserServiceClient {

    @PostMapping("/api/users/internal/authenticate")
    InternalAuthenticatedUserResponse authenticate(@RequestBody InternalAuthenticateRequest request);
}
