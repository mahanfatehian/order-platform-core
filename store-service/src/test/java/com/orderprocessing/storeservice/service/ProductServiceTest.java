package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.dto.ProductRequest;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The admin product form carries an "Active in catalog" checkbox and web-ui puts it in the request body, so the
 * store service has to act on it. Ignoring it means the checkbox silently does nothing and a product an admin
 * meant to keep out of the catalog is on sale anyway.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, inventoryRepository);
    }

    @Test
    void createsAnInactiveProductWhenTheRequestSaysSo() {
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDTO created = service.createProduct(request("SKU-1", false));

        assertThat(created.isActive()).isFalse();
    }

    @Test
    void createsAnActiveProductByDefault() {
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createProduct(request("SKU-2", null)).isActive()).isTrue();
        assertThat(service.createProduct(request("SKU-3", true)).isActive()).isTrue();
    }

    @Test
    void takesAProductOutOfTheCatalogOnUpdate() {
        Product existing = existingProduct(true);
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findById(existing.getId())).thenReturn(Optional.empty());

        ProductDTO updated = service.updateProduct(existing.getId(), request("SKU-4", false));

        assertThat(updated.isActive()).isFalse();
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void leavesTheFlagAloneWhenAnUpdateOmitsIt() {
        Product existing = existingProduct(false);
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findById(existing.getId())).thenReturn(Optional.empty());

        assertThat(service.updateProduct(existing.getId(), request("SKU-5", null)).isActive()).isFalse();
    }

    private ProductRequest request(String sku, Boolean active) {
        ProductRequest request = new ProductRequest();
        request.setName("Signal Lamp");
        request.setSku(sku);
        request.setPrice(new BigDecimal("29.90"));
        request.setCategory(Product.Category.OTHER);
        request.setActive(active);
        return request;
    }

    private Product existingProduct(boolean active) {
        Instant now = Instant.now();
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Signal Lamp");
        product.setSku("SKU-EXISTING");
        product.setPrice(new BigDecimal("29.90"));
        product.setCategory(Product.Category.OTHER);
        product.setActive(active);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }
}
