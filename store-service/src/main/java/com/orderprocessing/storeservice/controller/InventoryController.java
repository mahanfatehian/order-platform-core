package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.InventoryDTO;
import com.orderprocessing.storeservice.dto.UpdateInventoryDTO;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/internal/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public InventoryDTO getProductInventory(@PathVariable UUID productId) {
        Inventory inventory = inventoryService.getInventoryByProductId(productId);
        return convertToDTO(inventory);
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProductInventory(@PathVariable UUID productId, 
                                     @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.updateInventory(productId, updateDTO.getQuantity());
    }

    @PostMapping("/{productId}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveInventory(@PathVariable UUID productId, 
                               @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.reserveInventory(productId, updateDTO.getQuantity());
    }

    @PostMapping("/{productId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseInventory(@PathVariable UUID productId, 
                               @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.releaseInventory(productId, updateDTO.getQuantity());
    }

    private InventoryDTO convertToDTO(Inventory inventory) {
        InventoryDTO dto = new InventoryDTO();
        dto.setProductId(inventory.getProductId());
        dto.setQuantity(inventory.getQuantity());
        dto.setReservedQuantity(inventory.getReservedQuantity());
        dto.setLastUpdated(inventory.getLastUpdated());
        return dto;
    }


    @PostMapping("/reserve-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveBatch(@RequestBody Map<UUID, Integer> items) {
        inventoryService.reserveBatch(items);
    }

    @PostMapping("/release-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseBatch(@RequestBody Map<UUID, Integer> items) {
        inventoryService.releaseBatch(items);
    }
}