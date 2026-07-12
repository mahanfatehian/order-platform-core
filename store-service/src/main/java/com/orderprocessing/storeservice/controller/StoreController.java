package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.PageResponse;
import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
@Validated
public class StoreController {
    private static final Set<String> SORT_FIELDS = Set.of("name", "price", "createdAt");
    private final ProductService productService;

    @GetMapping("/products")
    public PageResponse<ProductDTO> getProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "false") boolean inStock) {
        return productService.getCatalog(q, inStock, pageRequest(page, size, sort));
    }

    @GetMapping("/products/{id}")
    public ProductDTO getProduct(@PathVariable UUID id) {
        return productService.getCatalogProduct(id);
    }

    static PageRequest pageRequest(int page, int size, String sortValue) {
        String[] parts = sortValue == null ? new String[0] : sortValue.split(",", 2);
        String property = parts.length == 0 || !SORT_FIELDS.contains(parts[0]) ? "createdAt" : parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
