package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store")
@Tag(name = "Store Catalog", description = "Public endpoints to manage and view store products")
@SecurityRequirement(name = "bearerAuth") // Applies JWT auth to all endpoints in this controller
public class StoreController {

    private final ProductService productService;

    public StoreController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get all products", description = "Returns a list of all available products in the store catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of products"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User lacks necessary permissions")
    })
    @GetMapping("/products")
    public List<ProductDTO> getAllProducts() {
        return productService.getAllProducts().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get product by ID", description = "Retrieves detailed information about a specific product.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Product not found with the specified ID")
    })
    @GetMapping("/products/{id}")
    public ProductDTO getProduct(@PathVariable UUID id) {
        return convertToDTO(productService.getProductById(id));
    }

    @Operation(summary = "Create a new product", description = "Adds a new product to the store catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only admins can create products")
    })
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDTO createProduct(@RequestBody ProductDTO productDTO) {
        Product product = convertToEntity(productDTO);
        return convertToDTO(productService.createProduct(product));
    }

    @Operation(summary = "Update an existing product", description = "Updates the details of an existing product.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only admins can update products"),
            @ApiResponse(responseCode = "404", description = "Product not found with the specified ID")
    })
    @PutMapping("/products/{id}")
    public ProductDTO updateProduct(@PathVariable UUID id, @RequestBody ProductDTO productDTO) {
        Product product = convertToEntity(productDTO);
        product.setId(id);
        return convertToDTO(productService.updateProduct(product));
    }

    @Operation(summary = "Delete a product", description = "Removes a product from the store catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only admins can delete products"),
            @ApiResponse(responseCode = "404", description = "Product not found with the specified ID")
    })
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
        dto.setPrice(product.getPrice());
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
        product.setPrice(dto.getPrice());
        product.setCategory(Product.Category.valueOf(dto.getCategory()));
        return product;
    }
}