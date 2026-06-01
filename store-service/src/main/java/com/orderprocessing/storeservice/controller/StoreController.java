package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final ProductService productService;

    public StoreController(ProductService productService) {
        this.productService = productService;
    }

    // Product Management Endpoints
    @GetMapping("/products")
    public List<ProductDTO> getAllProducts() {
        return productService.getAllProducts().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/products/{id}")
    public ProductDTO getProduct(@PathVariable UUID id) {
        return convertToDTO(productService.getProductById(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDTO createProduct(@RequestBody ProductDTO productDTO) {
        Product product = convertToEntity(productDTO);
        return convertToDTO(productService.createProduct(product));
    }

    @PutMapping("/products/{id}")
    public ProductDTO updateProduct(@PathVariable UUID id, @RequestBody ProductDTO productDTO) {
        Product product = convertToEntity(productDTO);
        product.setId(id);
        return convertToDTO(productService.updateProduct(product));
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
    }

    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());          // BigDecimal now
        dto.setCategory(product.getCategory().toString());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }

    private Product convertToEntity(ProductDTO dto) {
        Product product = new Product();
        product.setId(dto.getId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());          // BigDecimal now
        product.setCategory(Product.Category.valueOf(dto.getCategory()));
        return product;
    }
}