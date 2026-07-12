package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.storeservice.dto.InventoryDTO;
import com.orderprocessing.storeservice.dto.PageResponse;
import com.orderprocessing.storeservice.exception.InvalidInventoryException;
import com.orderprocessing.storeservice.exception.ResourceNotFoundException;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.InventoryReservation;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final ProcessedKafkaEventRepository processedEventRepository;
    private final StoreOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryReservationRepository reservationRepository,
                            ProductRepository productRepository,
                            ProcessedKafkaEventRepository processedEventRepository,
                            StoreOutboxEventRepository outboxEventRepository,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InventoryDTO getInventory(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        return toDto(product, inventory);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDTO> getInventoryPage(String query, Pageable pageable) {
        Page<Product> products = productRepository.searchAdmin(query == null ? "" : query.trim(), pageable);
        Map<UUID, Inventory> inventory = inventoryRepository
                .findAllById(products.getContent().stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return PageResponse.from(products, product -> {
            Inventory stock = inventory.get(product.getId());
            if (stock == null) {
                throw new ResourceNotFoundException("Inventory not found");
            }
            return toDto(product, stock);
        });
    }

    @Transactional
    public InventoryDTO updateInventory(UUID productId, int quantity) {
        List<Inventory> locked = inventoryRepository.findAllForUpdate(List.of(productId));
        if (locked.isEmpty()) {
            throw new ResourceNotFoundException("Inventory not found");
        }
        Inventory inventory = locked.getFirst();
        if (quantity < inventory.getReservedQuantity()) {
            throw new InvalidInventoryException("Total quantity cannot be below reserved quantity");
        }
        inventory.setQuantity(quantity);
        inventory.setLastUpdated(Instant.now());
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toDto(product, inventoryRepository.save(inventory));
    }

    @Transactional
    public void processOrderPlaced(OrderPlacedEvent event, String topic, int partition, long offset) {
        if (!markProcessed(event, topic, partition, offset)) {
            return;
        }
        validateOrderEvent(event.getOrderId(), event.getItems());

        List<InventoryReservation> existing = reservationRepository.findByOrderIdForUpdate(event.getOrderId());
        if (!existing.isEmpty()) {
            return;
        }

        Map<UUID, Integer> requested = orderedItems(event.getItems());
        List<Product> products = productRepository.findAllByIdOrdered(requested.keySet());
        Map<UUID, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Inventory> inventories = inventoryRepository.findAllForUpdate(requested.keySet());
        Map<UUID, Inventory> inventoryById = inventories.stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        String rejectionReason = null;
        for (Map.Entry<UUID, Integer> item : requested.entrySet()) {
            Product product = productById.get(item.getKey());
            Inventory inventory = inventoryById.get(item.getKey());
            if (product == null || inventory == null) {
                rejectionReason = "Product is unavailable";
                break;
            }
            if (!product.isActive()) {
                rejectionReason = "Product is inactive: " + product.getName();
                break;
            }
            if (inventory.getAvailableQuantity() < item.getValue()) {
                rejectionReason = "Insufficient inventory for product: " + product.getName();
                break;
            }
        }

        if (rejectionReason != null) {
            StockInsufficientEvent result = new StockInsufficientEvent();
            result.setOrderId(event.getOrderId());
            result.setSuccess(false);
            result.setReason(rejectionReason);
            result.setCorrelationId(event.getCorrelationId());
            addOutbox(event.getOrderId(), KafkaTopics.STORE_EVENTS, result);
            return;
        }

        Instant now = Instant.now();
        List<InventoryReservation> reservations = new ArrayList<>();
        for (Map.Entry<UUID, Integer> item : requested.entrySet()) {
            Inventory inventory = inventoryById.get(item.getKey());
            inventory.setReservedQuantity(Math.addExact(inventory.getReservedQuantity(), item.getValue()));
            inventory.setLastUpdated(now);

            InventoryReservation reservation = new InventoryReservation();
            reservation.setId(UUID.randomUUID());
            reservation.setOrderId(event.getOrderId());
            reservation.setProductId(item.getKey());
            reservation.setQuantity(item.getValue());
            reservation.setStatus(InventoryReservation.Status.RESERVED);
            reservation.setCreatedAt(now);
            reservation.setUpdatedAt(now);
            reservations.add(reservation);
        }
        inventoryRepository.saveAll(inventories);
        reservationRepository.saveAll(reservations);

        StockReservedEvent result = new StockReservedEvent();
        result.setOrderId(event.getOrderId());
        result.setSuccess(true);
        result.setCorrelationId(event.getCorrelationId());
        addOutbox(event.getOrderId(), KafkaTopics.STORE_EVENTS, result);
    }

    @Transactional
    public void processOrderCancelled(OrderCancelledEvent event, String topic, int partition, long offset) {
        if (!markProcessed(event, topic, partition, offset)) {
            return;
        }
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("Order id is required");
        }

        List<InventoryReservation> reservations = reservationRepository.findByOrderIdForUpdate(event.getOrderId());
        List<InventoryReservation> active = reservations.stream()
                .filter(r -> r.getStatus() == InventoryReservation.Status.RESERVED)
                .sorted(Comparator.comparing(InventoryReservation::getProductId))
                .toList();
        if (active.isEmpty()) {
            return;
        }

        List<UUID> productIds = active.stream().map(InventoryReservation::getProductId).toList();
        Map<UUID, Inventory> inventory = inventoryRepository.findAllForUpdate(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        if (inventory.size() != productIds.size()) {
            throw new IllegalStateException("Reserved inventory row is missing");
        }

        Instant now = Instant.now();
        for (InventoryReservation reservation : active) {
            Inventory stock = inventory.get(reservation.getProductId());
            if (stock.getReservedQuantity() < reservation.getQuantity()) {
                throw new IllegalStateException("Inventory reservation invariant was violated");
            }
            stock.setReservedQuantity(stock.getReservedQuantity() - reservation.getQuantity());
            stock.setLastUpdated(now);
            reservation.setStatus(InventoryReservation.Status.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setUpdatedAt(now);
        }
        inventoryRepository.saveAll(inventory.values());
        reservationRepository.saveAll(active);
    }

    private boolean markProcessed(DomainEvent event, String topic, int partition, long offset) {
        UUID eventId = event.getEventId();
        if (eventId == null) {
            eventId = UUID.nameUUIDFromBytes((topic + ':' + partition + ':' + offset)
                    .getBytes(StandardCharsets.UTF_8));
            event.setEventId(eventId);
        }
        return processedEventRepository.insertIfAbsent(eventId, event.getClass().getSimpleName(), topic,
                partition, offset) == 1;
    }

    private void addOutbox(UUID orderId, String topic, DomainEvent event) {
        StoreOutboxEvent outbox = new StoreOutboxEvent();
        outbox.setId(event.getEventId());
        outbox.setAggregateId(orderId.toString());
        outbox.setTopic(topic);
        outbox.setEventType(KafkaEventRegistry.eventType(event));
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize store event", ex);
        }
        outbox.setCreatedAt(Instant.now());
        outbox.setPublished(false);
        outbox.setAttemptCount(0);
        outbox.setDeadLettered(false);
        outboxEventRepository.save(outbox);
    }

    private Map<UUID, Integer> orderedItems(Map<UUID, Integer> items) {
        return items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private void validateOrderEvent(UUID orderId, Map<UUID, Integer> items) {
        if (orderId == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order id and items are required");
        }
        if (items.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 1)) {
            throw new IllegalArgumentException("Order item quantities must be positive");
        }
    }

    private InventoryDTO toDto(Product product, Inventory inventory) {
        InventoryDTO dto = new InventoryDTO();
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setSku(product.getSku());
        dto.setQuantity(inventory.getQuantity());
        dto.setReservedQuantity(inventory.getReservedQuantity());
        dto.setAvailableQuantity(inventory.getAvailableQuantity());
        dto.setLastUpdated(inventory.getLastUpdated());
        return dto;
    }
}
