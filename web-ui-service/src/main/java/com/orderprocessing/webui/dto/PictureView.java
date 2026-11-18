package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.UUID;

public record PictureView(UUID id, UUID productId, String contentType, Instant createdAt) {
}
