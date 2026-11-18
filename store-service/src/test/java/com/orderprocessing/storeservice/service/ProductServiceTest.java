package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.dto.ProductRequest;
import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.model.ProductCategory;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductCategoryRepository;
import com.orderprocessing.storeservice.repository.ProductPictureRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private ProductPictureRepository pictureRepository;

    private ProductService service;
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, inventoryRepository, categoryRepository, pictureRepository);
    }

    @Test
    void createsAnInactiveProductWhenTheRequestSaysSo() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDTO created = service.createProduct(request("SKU-1", false));

        assertThat(created.isActive()).isFalse();
        assertThat(created.getCategory()).isEqualTo("Other");
        assertThat(created.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void rejectsAnActiveCreateWhenTheProductHasNoPicture() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));

        assertThatThrownBy(() -> service.createProduct(request("SKU-2", null)))
                .isInstanceOf(CatalogRuleException.class);
        assertThatThrownBy(() -> service.createProduct(request("SKU-3", true)))
                .isInstanceOf(CatalogRuleException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void takesAProductOutOfTheCatalogOnUpdate() {
        Product existing = existingProduct(true);
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findById(existing.getId())).thenReturn(Optional.empty());

        assertThat(service.updateProduct(existing.getId(), request("SKU-5", null)).isActive()).isFalse();
    }

    @Test
    void rejectsAnActiveUpdateWhenTheProductHasNoPicture() {
        Product existing = existingProduct(false);
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));

        assertThatThrownBy(() -> service.updateProduct(existing.getId(), request("SKU-6", true)))
                .isInstanceOf(CatalogRuleException.class);
        assertThat(existing.isActive()).isFalse();
        verify(productRepository, never()).save(any());
    }

    private ProductRequest request(String sku, Boolean active) {
        ProductRequest request = new ProductRequest();
        request.setName("Signal Lamp");
        request.setSku(sku);
        request.setPrice(new BigDecimal("29.90"));
        request.setCategoryId(categoryId);
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
        product.setCategory(category());
        product.setActive(active);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }

    private ProductCategory category() {
        ProductCategory category = new ProductCategory();
        category.setId(categoryId);
        category.setName("Other");
        return category;
    }
}
