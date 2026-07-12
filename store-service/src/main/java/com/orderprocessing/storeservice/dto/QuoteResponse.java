package com.orderprocessing.storeservice.dto;

import java.util.List;

public record QuoteResponse(List<QuoteItemResponse> items) {
}
