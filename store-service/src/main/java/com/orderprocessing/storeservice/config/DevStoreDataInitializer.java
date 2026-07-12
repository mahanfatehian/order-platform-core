package com.orderprocessing.storeservice.config;

import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("dev")
public class DevStoreDataInitializer implements ApplicationRunner {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public DevStoreDataInitializer(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("a1111111-1111-1111-1111-111111111111", "Wireless Headphones", "ELEC-001",
                "Noise-cancelling over-ear headphones", "149.99", Product.Category.ELECTRONICS, 100);
        seed("a2222222-2222-2222-2222-222222222222", "Cotton T-Shirt", "CLOTH-001",
                "Classic fit cotton t-shirt in navy blue", "19.99", Product.Category.CLOTHING, 250);
        seed("a3333333-3333-3333-3333-333333333333", "Coffee Table", "HOME-001",
                "Solid oak coffee table with storage", "249.00", Product.Category.HOME, 12);
        seed("a4444444-4444-4444-4444-444444444444", "Mystery Novel", "BOOK-001",
                "Bestselling crime thriller paperback", "12.50", Product.Category.BOOKS, 0);
        seed("a5555555-5555-5555-5555-555555555555", "Ceramic Mug", "OTHER-001",
                "Handmade ceramic coffee mug, 350ml", "8.95", Product.Category.OTHER, 7);
    }

    private void seed(String idValue, String name, String sku, String description, String price,
                      Product.Category category, int quantity) {
        UUID id = UUID.fromString(idValue);
        if (productRepository.existsById(id)) {
            return;
        }
        Instant now = Instant.now();
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setCategory(category);
        product.setActive(true);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProductId(id);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(0);
        inventory.setLastUpdated(now);
        inventoryRepository.save(inventory);
    }
}
