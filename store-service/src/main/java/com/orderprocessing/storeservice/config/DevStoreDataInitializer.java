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
    public static final int SHOWCASE_PRODUCT_COUNT = 15;

    private static final List<SeedProduct> SHOWCASE_CATALOG = List.of(
            new SeedProduct("a1111111-1111-1111-1111-111111111111",
                    "Aurora ANC Wireless Headphones", "ELEC-AUR-1001",
                    "Over-ear Bluetooth headphones with adaptive noise cancellation, multipoint pairing, "
                            + "and up to 35 hours of battery life.",
                    "179.99", Product.Category.ELECTRONICS, 42),
            new SeedProduct("a4444444-4444-4444-4444-444444444444",
                    "Nimbus 8-in-1 USB-C Hub", "ELEC-NIM-1002",
                    "Compact aluminum hub with 4K HDMI, Gigabit Ethernet, SD and microSD readers, USB-A, "
                            + "and 100W power delivery.",
                    "69.95", Product.Category.ELECTRONICS, 85),
            new SeedProduct("a6666666-6666-6666-6666-666666666666",
                    "Lumen Portable Bluetooth Speaker", "ELEC-LUM-1003",
                    "Water-resistant portable speaker with balanced 360-degree sound, USB-C charging, "
                            + "and an 18-hour battery.",
                    "89.00", Product.Category.ELECTRONICS, 0),
            new SeedProduct("a7777777-7777-7777-7777-777777777777",
                    "Arc Wireless Mechanical Keyboard", "ELEC-ARC-1004",
                    "Low-profile mechanical keyboard with hot-swappable switches, white backlighting, "
                            + "and three-device wireless pairing.",
                    "119.50", Product.Category.ELECTRONICS, 36),
            new SeedProduct("a8888888-8888-8888-8888-888888888888",
                    "Pulse GPS Fitness Watch", "ELEC-PLS-1005",
                    "Lightweight fitness watch with dual-band GPS, heart-rate and sleep tracking, "
                            + "and seven-day battery life.",
                    "229.00", Product.Category.ELECTRONICS, 24),

            new SeedProduct("a2222222-2222-2222-2222-222222222222",
                    "Harbor Organic Cotton Oxford Shirt", "CLTH-HBR-2001",
                    "Midweight organic-cotton Oxford shirt with a tailored everyday fit, reinforced seams, "
                            + "and pearl-finish buttons.",
                    "64.00", Product.Category.CLOTHING, 60),
            new SeedProduct("a9999999-9999-9999-9999-999999999999",
                    "Meridian Everyday Chino Pants", "CLTH-MER-2002",
                    "Garment-dyed stretch chinos with a clean tapered cut, reinforced pockets, "
                            + "and a comfortable mid-rise waist.",
                    "78.00", Product.Category.CLOTHING, 48),
            new SeedProduct("aa111111-1111-1111-1111-111111111111",
                    "Northline Recycled-Fill Puffer Jacket", "CLTH-NTH-2003",
                    "Weather-resistant insulated jacket with recycled fill, an adjustable hem, "
                            + "and packable interior pocket.",
                    "149.00", Product.Category.CLOTHING, 18),
            new SeedProduct("ab111111-1111-1111-1111-111111111111",
                    "Coastline Merino Crewneck Sweater", "CLTH-CST-2004",
                    "Fine-gauge merino wool crewneck with temperature-regulating comfort and ribbed trim "
                            + "for year-round layering.",
                    "110.00", Product.Category.CLOTHING, 32),
            new SeedProduct("ac111111-1111-1111-1111-111111111111",
                    "Atlas Leather Everyday Sneakers", "CLTH-ATL-2005",
                    "Full-grain leather sneakers with a cushioned recycled-foam footbed and durable "
                            + "stitched rubber outsole.",
                    "129.00", Product.Category.CLOTHING, 27),

            new SeedProduct("a3333333-3333-3333-3333-333333333333",
                    "Hearthstone Enameled Dutch Oven", "HOME-HRT-3001",
                    "Five-quart enameled cast-iron Dutch oven designed for even heat retention, oven use, "
                            + "and easy cleanup.",
                    "94.99", Product.Category.HOME, 22),
            new SeedProduct("a5555555-5555-5555-5555-555555555555",
                    "Vale Washed Linen Duvet Cover Set", "HOME-VLE-3002",
                    "Breathable European-flax linen duvet cover with two matching shams, corner ties, "
                            + "and a hidden button closure.",
                    "139.00", Product.Category.HOME, 16),
            new SeedProduct("ad111111-1111-1111-1111-111111111111",
                    "Rowan Oak Bedside Lamp", "HOME-ROW-3003",
                    "Dimmable bedside lamp with a solid oak base, linen shade, warm LED bulb, "
                            + "and integrated USB-C charging port.",
                    "74.50", Product.Category.HOME, 40),
            new SeedProduct("ae111111-1111-1111-1111-111111111111",
                    "Summit Cordless Stick Vacuum", "HOME-SUM-3004",
                    "Lightweight cordless vacuum with a sealed HEPA filtration system, motorized floor head, "
                            + "and up to 50 minutes of runtime.",
                    "249.99", Product.Category.HOME, 14),
            new SeedProduct("af111111-1111-1111-1111-111111111111",
                    "Haven Glass Food Storage Set", "HOME-HVN-3005",
                    "Ten-piece borosilicate glass container set with locking leak-resistant lids, "
                            + "nesting shapes, and freezer-safe construction.",
                    "49.95", Product.Category.HOME, 75)
    );

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public DevStoreDataInitializer(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (SHOWCASE_CATALOG.size() != SHOWCASE_PRODUCT_COUNT) {
            throw new IllegalStateException("The development catalog must contain exactly 15 products");
        }
        SHOWCASE_CATALOG.forEach(this::seed);
    }

    private void seed(SeedProduct seed) {
        UUID id = UUID.fromString(seed.id());
        if (!productRepository.existsById(id)) {
            Instant now = Instant.now();
            Product product = new Product();
            product.setId(id);
            product.setName(seed.name());
            product.setSku(seed.sku());
            product.setDescription(seed.description());
            product.setPrice(new BigDecimal(seed.price()));
            product.setCategory(seed.category());
            product.setActive(true);
            product.setCreatedAt(now);
            product.setUpdatedAt(now);
            productRepository.save(product);
        }

        if (!inventoryRepository.existsById(id)) {
            Inventory inventory = new Inventory();
            inventory.setProductId(id);
            inventory.setQuantity(seed.quantity());
            inventory.setReservedQuantity(0);
            inventory.setLastUpdated(Instant.now());
            inventoryRepository.save(inventory);
        }
    }

    private record SeedProduct(String id, String name, String sku, String description, String price,
                               Product.Category category, int quantity) {
    }
}
