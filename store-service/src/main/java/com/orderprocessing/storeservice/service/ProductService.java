package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.dto.PageResponse;
import com.orderprocessing.storeservice.dto.ProductDTO;
import com.orderprocessing.storeservice.dto.ProductRequest;
import com.orderprocessing.storeservice.dto.QuoteItemResponse;
import com.orderprocessing.storeservice.dto.QuoteRequest;
import com.orderprocessing.storeservice.dto.QuoteResponse;
import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.exception.DuplicateResourceException;
import com.orderprocessing.storeservice.exception.ResourceNotFoundException;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.model.ProductCategory;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductCategoryRepository;
import com.orderprocessing.storeservice.repository.ProductPictureRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductPictureRepository pictureRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository,
                          ProductCategoryRepository categoryRepository, ProductPictureRepository pictureRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryRepository = categoryRepository;
        this.pictureRepository = pictureRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDTO> getCatalog(String query, boolean inStock, Pageable pageable) {
        Page<Product> products = productRepository.searchCatalog(normalizeQuery(query), inStock, pageable);
        Map<UUID, Inventory> inventory = inventoryByProduct(products.getContent());
        Map<UUID, UUID> pictures = firstPictureByProduct(products.getContent());
        return PageResponse.from(products, product -> toDto(product, inventory.get(product.getId()),
                pictures.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public ProductDTO getCatalogProduct(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toDto(product, inventoryRepository.findById(id).orElse(null), firstPicture(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDTO> getAdminProducts(String query, Pageable pageable) {
        Page<Product> products = productRepository.searchAdmin(normalizeQuery(query), pageable);
        Map<UUID, Inventory> inventory = inventoryByProduct(products.getContent());
        Map<UUID, UUID> pictures = firstPictureByProduct(products.getContent());
        return PageResponse.from(products, product -> toDto(product, inventory.get(product.getId()),
                pictures.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public ProductDTO getAdminProduct(UUID id) {
        Product product = findProduct(id);
        return toDto(product, inventoryRepository.findById(id).orElse(null), firstPicture(id));
    }

    @Transactional
    public ProductDTO createProduct(ProductRequest request) {
        String sku = normalizeSku(request.getSku());
        assertSkuAvailable(sku, null);

        Instant now = Instant.now();
        Product product = new Product();
        product.setId(UUID.randomUUID());
        apply(product, request, sku);
        boolean active = request.getActive() == null || request.getActive();
        requirePictureWhenActive(product.getId(), active);
        product.setActive(active);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProductId(product.getId());
        inventory.setQuantity(0);
        inventory.setReservedQuantity(0);
        inventory.setLastUpdated(now);
        inventoryRepository.save(inventory);
        return toDto(product, inventory, null);
    }

    @Transactional
    public ProductDTO updateProduct(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        String sku = normalizeSku(request.getSku());
        assertSkuAvailable(sku, id);
        apply(product, request, sku);
        boolean active = request.getActive() == null ? product.isActive() : request.getActive();
        requirePictureWhenActive(product.getId(), active);
        product.setActive(active);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        return toDto(product, inventoryRepository.findById(id).orElse(null), firstPicture(id));
    }

    @Transactional
    public ProductDTO setActive(UUID id, boolean active) {
        Product product = findProduct(id);
        requirePictureWhenActive(id, active);
        product.setActive(active);
        product.setUpdatedAt(Instant.now());
        return toDto(productRepository.save(product), inventoryRepository.findById(id).orElse(null), firstPicture(id));
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest request) {
        Map<UUID, Integer> requested = new HashMap<>();
        request.items().forEach(item -> requested.merge(item.productId(), item.quantity(), Math::addExact));
        if (requested.size() != request.items().size()) {
            throw new IllegalArgumentException("Duplicate product IDs are not allowed in a quote");
        }

        List<Product> products = productRepository.findAllByIdOrdered(requested.keySet());
        if (products.size() != requested.size()) {
            throw new ResourceNotFoundException("One or more products were not found");
        }
        Map<UUID, Inventory> inventory = inventoryRepository.findAllById(requested.keySet()).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        List<QuoteItemResponse> items = products.stream().map(product -> {
            Inventory stock = inventory.get(product.getId());
            int available = stock == null ? 0 : stock.getAvailableQuantity();
            int quantity = requested.get(product.getId());
            return new QuoteItemResponse(product.getId(), product.getName(), product.getPrice(), product.isActive(),
                    quantity, available, product.isActive() && available >= quantity);
        }).toList();
        return new QuoteResponse(items);
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void apply(Product product, ProductRequest request, String sku) {
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        product.setSku(sku);
        product.setPrice(request.getPrice());
        product.setCategory(category(request.getCategoryId()));
    }

    private ProductCategory category(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void requirePictureWhenActive(UUID productId, boolean active) {
        if (active && !pictureRepository.existsByProductId(productId)) {
            throw new CatalogRuleException(HttpStatus.CONFLICT, "PICTURE_REQUIRED",
                    "A product needs a stored picture before it can be active");
        }
    }

    private UUID firstPicture(UUID productId) {
        return firstPictureByProduct(List.of(findProduct(productId))).get(productId);
    }

    private Map<UUID, UUID> firstPictureByProduct(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> first = new HashMap<>();
        pictureRepository.findByProductIdInOrderByCreatedAtAsc(products.stream().map(Product::getId).toList())
                .forEach(picture -> first.putIfAbsent(picture.getProductId(), picture.getId()));
        return first;
    }

    private void assertSkuAvailable(String sku, UUID excludedId) {
        if (sku == null) {
            return;
        }
        boolean exists = excludedId == null
                ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, excludedId);
        if (exists) {
            throw new DuplicateResourceException("SKU is already in use");
        }
    }

    private Map<UUID, Inventory> inventoryByProduct(List<Product> products) {
        List<UUID> ids = products.stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
    }

    private ProductDTO toDto(Product product, Inventory inventory, UUID pictureId) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setSku(product.getSku());
        dto.setPrice(product.getPrice());
        dto.setCategory(product.getCategory().getName());
        dto.setCategoryId(product.getCategory().getId());
        dto.setPictureId(pictureId);
        dto.setActive(product.isActive());
        dto.setAvailableQuantity(inventory == null ? 0 : inventory.getAvailableQuantity());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
