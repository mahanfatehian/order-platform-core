package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.config.StoreServiceFeignConfig;
import com.orderprocessing.orderservice.dto.StoreQuoteRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "store-service",
        configuration = StoreServiceFeignConfig.class
)
public interface StoreServiceClient {
    @PostMapping("/api/store/internal/products/quote")
    StoreQuoteResponse quote(@RequestBody StoreQuoteRequest request);
}
