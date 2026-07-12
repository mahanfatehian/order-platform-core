package com.orderprocessing.orderservice.dto;

import java.util.List;

public record StoreQuoteRequest(List<StoreQuoteItemRequest> items) {
}
