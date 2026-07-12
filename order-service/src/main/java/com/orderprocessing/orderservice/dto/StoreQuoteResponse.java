package com.orderprocessing.orderservice.dto;

import java.util.List;

public record StoreQuoteResponse(List<StoreQuoteItemResponse> items) {
}
