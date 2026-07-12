package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.ActivationRequest;
import com.orderprocessing.storeservice.dto.InventoryDTO;
import com.orderprocessing.storeservice.dto.PageResponse;
import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.dto.ProductRequest;
import com.orderprocessing.storeservice.dto.UpdateInventoryDTO;
import com.orderprocessing.storeservice.service.InventoryService;
import com.orderprocessing.storeservice.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminStoreController {
    private final ProductService productService;
    private final InventoryService inventoryService;

    @GetMapping("/products")
    public PageResponse<ProductDTO> products(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return productService.getAdminProducts(q, StoreController.pageRequest(page, size, sort));
    }

    @GetMapping("/products/{id}")
    public ProductDTO product(@PathVariable UUID id) {
        return productService.getAdminProduct(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDTO createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductDTO updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @PatchMapping("/products/{id}/activation")
    public ProductDTO setProductActivation(@PathVariable UUID id,
                                            @Valid @RequestBody ActivationRequest request) {
        return productService.setActive(id, request.active());
    }

    @GetMapping("/inventory")
    public PageResponse<InventoryDTO> inventory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return inventoryService.getInventoryPage(q, StoreController.pageRequest(page, size, sort));
    }

    @GetMapping("/inventory/{productId}")
    public InventoryDTO inventory(@PathVariable UUID productId) {
        return inventoryService.getInventory(productId);
    }

    @PutMapping("/inventory/{productId}")
    public InventoryDTO updateInventory(@PathVariable UUID productId,
                                        @Valid @RequestBody UpdateInventoryDTO request) {
        return inventoryService.updateInventory(productId, request.getQuantity());
    }
}
