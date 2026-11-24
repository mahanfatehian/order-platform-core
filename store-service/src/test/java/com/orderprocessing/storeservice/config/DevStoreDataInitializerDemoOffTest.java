package com.orderprocessing.storeservice.config;

import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductCategoryRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DevStoreDataInitializerDemoOffTest {
    @Test
    void demoModeOffDoesNotSeedProducts() {
        ProductRepository products = mock(ProductRepository.class);
        new DevStoreDataInitializer(products, mock(InventoryRepository.class),
                mock(ProductCategoryRepository.class), false).run(null);
        verify(products, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
