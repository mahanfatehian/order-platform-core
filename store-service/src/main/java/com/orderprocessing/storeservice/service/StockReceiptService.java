package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.dto.StockReceiptResponse;
import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.exception.ResourceNotFoundException;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.StockReceipt;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StockReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StockReceiptService {
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final StockReceiptRepository receipts;

    public StockReceiptService(ProductRepository products, InventoryRepository inventory,
                               StockReceiptRepository receipts) {
        this.products = products;
        this.inventory = inventory;
        this.receipts = receipts;
    }

    @Transactional
    public StockReceiptResponse receiveStock(UUID productId, int quantity, String reason, String idempotencyKey) {
        if (quantity <= 0) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "QUANTITY_NOT_POSITIVE",
                    "Received quantity must be positive");
        }
        String normalizedReason = requireText(reason, "Reason");
        String key = requireText(idempotencyKey, "Idempotency key");
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }
        String hash = hash(productId, quantity, normalizedReason);
        Optional<StockReceipt> existing = receipts.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        Inventory stock = lockedInventory(productId);
        try {
            stock.setQuantity(Math.addExact(stock.getQuantity(), quantity));
        } catch (ArithmeticException ex) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "QUANTITY_OVERFLOW",
                    "On-hand quantity would overflow");
        }
        stock.setLastUpdated(Instant.now());
        inventory.save(stock);

        StockReceipt receipt = new StockReceipt();
        receipt.setId(UUID.randomUUID());
        receipt.setProductId(productId);
        receipt.setQuantity(quantity);
        receipt.setReason(normalizedReason);
        receipt.setIdempotencyKey(key);
        receipt.setRequestHash(hash);
        receipt.setCreatedAt(Instant.now());
        return StockReceiptResponse.from(receipts.save(receipt));
    }

    private StockReceiptResponse replay(StockReceipt existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new CatalogRuleException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "This key was already used for a different stock receipt");
        }
        return StockReceiptResponse.from(existing);
    }

    private Inventory lockedInventory(UUID productId) {
        List<Inventory> locked = inventory.findAllForUpdate(List.of(productId));
        if (!locked.isEmpty()) {
            return locked.getFirst();
        }
        Inventory created = new Inventory();
        created.setProductId(productId);
        created.setQuantity(0);
        created.setReservedQuantity(0);
        created.setLastUpdated(Instant.now());
        inventory.saveAndFlush(created);
        return inventory.findAllForUpdate(List.of(productId)).getFirst();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "MISSING_TEXT", field + " is required");
        }
        return value.trim();
    }

    private static String hash(UUID productId, int quantity, String reason) {
        String body = productId + "|" + quantity + "|" + reason;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
