package com.orderprocessing.storeservice.controller;

import com.orderprocessing.storeservice.dto.InventoryDTO;
import com.orderprocessing.storeservice.dto.UpdateInventoryDTO;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/internal/inventory")
@Tag(name = "Store Inventory (Internal)", description = "Internal endpoints for inventory management (Requires X-Store-Internal-Api-Key)")
@SecurityRequirement(name = "internalApiKey") // Tells Swagger this requires the API Key header
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Get inventory by product ID", description = "Retrieves current stock and reserved quantities for a specific product.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the specified product")
    })
    @GetMapping("/{productId}")
    public InventoryDTO getProductInventory(@PathVariable UUID productId) {
        Inventory inventory = inventoryService.getInventoryByProductId(productId);
        return convertToDTO(inventory);
    }

    @Operation(summary = "Update total inventory quantity", description = "Directly updates the total quantity of a product in stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inventory updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the specified product")
    })
    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProductInventory(@PathVariable UUID productId,
                                       @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.updateInventory(productId, updateDTO.getQuantity());
    }

    @Operation(summary = "Reserve inventory for a product", description = "Reserves a specific quantity of a product (increments reserved quantity).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inventory reserved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the specified product"),
            @ApiResponse(responseCode = "409", description = "Conflict - Insufficient available inventory to reserve")
    })
    @PostMapping("/{productId}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveInventory(@PathVariable UUID productId,
                                 @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.reserveInventory(productId, updateDTO.getQuantity());
    }

    @Operation(summary = "Release reserved inventory", description = "Releases previously reserved inventory (decrements reserved quantity).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inventory released successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the specified product")
    })
    @PostMapping("/{productId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseInventory(@PathVariable UUID productId,
                                 @RequestBody UpdateInventoryDTO updateDTO) {
        inventoryService.releaseInventory(productId, updateDTO.getQuantity());
    }

    @Operation(summary = "Reserve batch inventory", description = "Synchronously reserves stock for multiple items in a single transaction. Used by Order Service.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Batch reservation successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key"),
            @ApiResponse(responseCode = "409", description = "Conflict - Insufficient available inventory for one or more items")
    })
    @PostMapping("/reserve-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveBatch(@RequestBody Map<UUID, Integer> items) {
        inventoryService.reserveBatch(items);
    }

    @Operation(summary = "Release batch inventory", description = "Synchronously releases reserved stock for multiple items. Used for order cancellation/compensation.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Batch release successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid X-Store-Internal-Api-Key")
    })
    @PostMapping("/release-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseBatch(@RequestBody Map<UUID, Integer> items) {
        inventoryService.releaseBatch(items);
    }

    private InventoryDTO convertToDTO(Inventory inventory) {
        InventoryDTO dto = new InventoryDTO();
        dto.setProductId(inventory.getProductId());
        dto.setQuantity(inventory.getQuantity());
        dto.setReservedQuantity(inventory.getReservedQuantity());
        dto.setLastUpdated(inventory.getLastUpdated());
        return dto;
    }
}