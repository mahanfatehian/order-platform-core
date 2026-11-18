package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.dto.ProductRequest;
import com.orderprocessing.storeservice.dto.QuoteItemRequest;
import com.orderprocessing.storeservice.dto.QuoteRequest;
import com.orderprocessing.storeservice.dto.StockReceiptResponse;
import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.ProductCategory;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductPictureRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StockReceiptRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, CategoryService.class, ProductPictureService.class, StockReceiptService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class MerchantCatalogTest {

    private static final byte[] PNG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresql(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Autowired private CategoryService categories;
    @Autowired private ProductService products;
    @Autowired private ProductPictureService pictures;
    @Autowired private StockReceiptService receipts;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductPictureRepository pictureRepository;
    @Autowired private StockReceiptRepository receiptRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void merchantCanCreateACategoryStoreAPictureReceiveStockAndFindTheProduct() {
        ProductCategory category = categories.create("Lamps");
        assertThat(categories.create("lamps").getId()).isEqualTo(category.getId());
        assertThat(categories.list()).filteredOn(row -> row.getName().equalsIgnoreCase("lamps")).hasSize(1);

        ProductDTO created = products.createProduct(request("Desk Lamp", "LAMP-1", "42.00", category.getId(), false));
        assertThat(created.isActive()).isFalse();
        assertThat(created.getPictureId()).isNull();

        pictures.store(created.getId(), "image/png", PNG);
        StockReceiptResponse first = receipts.receiveStock(created.getId(), 4, "opening delivery", "recv-1");
        Inventory stock = inventoryRepository.findById(created.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(4);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(first.quantity()).isEqualTo(4);

        stock.setReservedQuantity(2);
        inventoryRepository.save(stock);
        receipts.receiveStock(created.getId(), 3, "second delivery", "recv-2");
        stock = inventoryRepository.findById(created.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(7);
        assertThat(stock.getReservedQuantity()).isEqualTo(2);

        StockReceiptResponse replay = receipts.receiveStock(created.getId(), 3, "second delivery", "recv-2");
        assertThat(replay.id()).isEqualTo(receiptRepository.findByIdempotencyKey("recv-2").orElseThrow().getId());
        assertThat(inventoryRepository.findById(created.getId()).orElseThrow().getQuantity()).isEqualTo(7);
        assertThat(receiptRepository.count()).isEqualTo(2);

        ProductDTO active = products.setActive(created.getId(), true);
        assertThat(active.isActive()).isTrue();
        assertThat(active.getCategory()).isEqualTo("Lamps");
        assertThat(active.getPictureId()).isNotNull();

        PageRequest page = PageRequest.of(0, 20);
        assertThat(productRepository.searchCatalog("Desk Lamp", false, page).getContent())
                .extracting(product -> product.getId()).contains(created.getId());
        assertThat(productRepository.searchCatalog("LAMP-1", false, page).getContent())
                .extracting(product -> product.getId()).contains(created.getId());

        ProductRequest priced = request("Desk Lamp", "LAMP-1", "55.50", category.getId(), true);
        products.updateProduct(created.getId(), priced);
        var quote = products.quote(new QuoteRequest(List.of(new QuoteItemRequest(created.getId(), 1))));
        assertThat(quote.items()).singleElement().satisfies(item ->
                assertThat(item.unitPrice()).isEqualByComparingTo("55.50"));
        assertThat(products.getCatalogProduct(created.getId()).getPrice()).isEqualByComparingTo("55.50");
        assertThat(receiptRepository.findAll()).allSatisfy(receipt -> assertThat(receipt.getQuantity()).isPositive());
    }

    @Test
    void anActiveProductWithNoPictureIsRejectedOnCreateAndUpdate() {
        ProductCategory category = categories.create("Glass");
        assertThatThrownBy(() -> products.createProduct(request("Bowl", "BOWL-1", "10.00", category.getId(), true)))
                .isInstanceOf(CatalogRuleException.class);
        assertThatThrownBy(() -> products.createProduct(request("Bowl", "BOWL-1", "10.00", category.getId(), null)))
                .isInstanceOf(CatalogRuleException.class);
        assertThat(productRepository.findAll()).isEmpty();

        ProductDTO inactive = products.createProduct(request("Bowl", "BOWL-1", "10.00", category.getId(), false));
        assertThatThrownBy(() -> products.updateProduct(inactive.getId(),
                request("Bowl", "BOWL-1", "10.00", category.getId(), true)))
                .isInstanceOf(CatalogRuleException.class);
        assertThat(products.getAdminProduct(inactive.getId()).isActive()).isFalse();
    }

    @Test
    void theSameReceiveKeyAddsStockOnceAndADifferentBodyConflicts() {
        ProductCategory category = categories.create("Tools");
        ProductDTO product = products.createProduct(request("Hammer", "HAM-1", "15.00", category.getId(), false));
        StockReceiptResponse first = receipts.receiveStock(product.getId(), 6, "crate", "same-key");
        StockReceiptResponse second = receipts.receiveStock(product.getId(), 6, "crate", "same-key");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(inventoryRepository.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(6);
        assertThat(inventoryRepository.findById(product.getId()).orElseThrow().getReservedQuantity()).isZero();
        assertThatThrownBy(() -> receipts.receiveStock(product.getId(), 9, "crate", "same-key"))
                .isInstanceOf(CatalogRuleException.class);
        assertThat(inventoryRepository.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(6);
    }

    @Test
    void aMissingInventoryRowIsCreatedAtZeroThenReceived() {
        ProductCategory category = categories.create("Spare");
        ProductDTO product = products.createProduct(request("Washer", "WASH-1", "1.00", category.getId(), false));
        entityManager.createQuery("delete from Inventory i where i.productId = :id")
                .setParameter("id", product.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        receipts.receiveStock(product.getId(), 2, "found stock", "missing-row");

        Inventory stock = inventoryRepository.findById(product.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(2);
        assertThat(stock.getReservedQuantity()).isZero();
    }

    @Test
    void aReceiveDoesNotChangeReservedQuantity() {
        ProductCategory category = categories.create("Paint");
        ProductDTO product = products.createProduct(request("Brush", "BRUSH-1", "8.00", category.getId(), false));
        Inventory stock = inventoryRepository.findById(product.getId()).orElseThrow();
        stock.setQuantity(10);
        stock.setReservedQuantity(4);
        inventoryRepository.saveAndFlush(stock);

        receipts.receiveStock(product.getId(), 5, "restock", "paint-key");

        Inventory after = inventoryRepository.findById(product.getId()).orElseThrow();
        assertThat(after.getQuantity()).isEqualTo(15);
        assertThat(after.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void aNonImageUploadIsRejectedAndStoresNothing() {
        ProductCategory category = categories.create("Paper");
        ProductDTO product = products.createProduct(request("Notebook", "NOTE-1", "4.00", category.getId(), false));

        assertThatThrownBy(() -> pictures.store(product.getId(), "image/gif", PNG))
                .isInstanceOf(CatalogRuleException.class);
        assertThatThrownBy(() -> pictures.store(product.getId(), "text/plain", "not-an-image".getBytes()))
                .isInstanceOf(CatalogRuleException.class);

        assertThat(pictureRepository.countByProductId(product.getId())).isZero();
        assertThat(pictureRepository.count()).isZero();
    }

    private ProductRequest request(String name, String sku, String price, UUID categoryId, Boolean active) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setSku(sku);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(categoryId);
        request.setActive(active);
        return request;
    }
}
