package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public Inventory getInventoryByProductId(UUID productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product ID: " + productId));
    }

    @Transactional
    public Inventory updateInventory(UUID productId, int quantity) {
        Inventory inventory = getInventoryByProductId(productId);
        inventory.setQuantity(quantity);
        inventory.setLastUpdated(java.time.Instant.now());
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory reserveInventory(UUID productId, int quantityToReserve) {
        Inventory inventory = getInventoryByProductId(productId);
        
        if (inventory.getQuantity() - inventory.getReservedQuantity() >= quantityToReserve) {
            inventory.setReservedQuantity(inventory.getReservedQuantity() + quantityToReserve);
            inventory.setLastUpdated(java.time.Instant.now());
            return inventoryRepository.save(inventory);
        } else {
            throw new RuntimeException("Insufficient inventory for product ID: " + productId);
        }
    }

    @Transactional
    public Inventory releaseInventory(UUID productId, int quantityToRelease) {
        Inventory inventory = getInventoryByProductId(productId);
        
        if (inventory.getReservedQuantity() >= quantityToRelease) {
            inventory.setReservedQuantity(inventory.getReservedQuantity() - quantityToRelease);
            inventory.setLastUpdated(java.time.Instant.now());
            return inventoryRepository.save(inventory);
        } else {
            throw new RuntimeException("Cannot release more inventory than is reserved for product ID: " + productId);
        }
    }
}