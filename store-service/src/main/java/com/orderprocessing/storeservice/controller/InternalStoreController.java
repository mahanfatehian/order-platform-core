package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.QuoteRequest;
import com.orderprocessing.storeservice.dto.QuoteResponse;
import com.orderprocessing.storeservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/internal/products")
@RequiredArgsConstructor
public class InternalStoreController {
    private final ProductService productService;

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return productService.quote(request);
    }
}
