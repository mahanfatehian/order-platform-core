package com.orderprocessing.orderservice.dto;

import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderReturn;

import java.util.UUID;

public record ReturnResponse(UUID id, UUID orderId, String status, String orderStatus, UUID refundEntryId) {
    public static ReturnResponse from(OrderReturn row, Order.Status orderStatus) {
        return new ReturnResponse(row.getId(), row.getOrderId(), row.getStatus().name(),
                orderStatus.name(), row.getRefundEntryId());
    }
}
