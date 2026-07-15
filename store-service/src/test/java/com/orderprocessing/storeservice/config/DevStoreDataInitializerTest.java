package com.orderprocessing.storeservice.config;

import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevStoreDataInitializerTest {

    @Test
    void createsExactlyFifteenProfessionalProductsAcrossShowcaseCategories() throws Exception {
        ProductRepository products = mock(ProductRepository.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(products.existsById(any())).thenReturn(false);
        when(inventory.existsById(any())).thenReturn(false);

        new DevStoreDataInitializer(products, inventory).run(null);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(products, times(DevStoreDataInitializer.SHOWCASE_PRODUCT_COUNT)).save(productCaptor.capture());
        List<Product> seededProducts = productCaptor.getAllValues();
        assertThat(seededProducts).hasSize(15);
        assertThat(seededProducts).extracting(Product::getSku).doesNotHaveDuplicates();
        assertThat(seededProducts).allSatisfy(product -> {
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getDescription()).hasSizeGreaterThan(60);
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getSku()).matches("(ELEC|CLTH|HOME)-[A-Z]{3}-[123][0-9]{3}");
        });
        assertThat(seededProducts).filteredOn(product -> product.getCategory() == Product.Category.ELECTRONICS)
                .hasSize(5);
        assertThat(seededProducts).filteredOn(product -> product.getCategory() == Product.Category.CLOTHING)
                .hasSize(5);
        assertThat(seededProducts).filteredOn(product -> product.getCategory() == Product.Category.HOME)
                .hasSize(5);

        ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventory, times(15)).save(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getAllValues()).extracting(Inventory::getProductId)
                .hasSize(15)
                .doesNotHaveDuplicates();
        assertThat(inventoryCaptor.getAllValues()).extracting(Inventory::getQuantity)
                .contains(0, 14, 85);
        assertThat(new HashSet<>(inventoryCaptor.getAllValues().stream()
                .map(Inventory::getQuantity)
                .toList())).hasSizeGreaterThan(10);
    }
}
