package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.config.StoreServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "store-service",
        configuration = StoreServiceFeignConfig.class
)
public interface StoreServiceClient {
    @PostMapping("/api/store/internal/inventory/reserve-batch")
    void reserveBatch(@RequestBody Map<UUID, Integer> items);

    @PostMapping("/api/store/internal/inventory/release-batch")
    void releaseBatch(@RequestBody Map<UUID, Integer> items);
}