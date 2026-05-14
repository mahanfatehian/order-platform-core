package com.orderprocessing.authservice.client;

import com.orderprocessing.authservice.client.dto.UserResponse;
import com.orderprocessing.authservice.config.FeignInternalAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "user-service",
        url = "${services.user-service.url}",
        configuration = FeignInternalAuthConfig.class
)
public interface UserServiceClient {

    @GetMapping("/api/users/internal/username/{username}")
    UserResponse getByUsername(@PathVariable("username") String username);

    @GetMapping("/api/users/internal/search")
    UserResponse getByUsernameOrEmail(@RequestParam("value") String value);
}
