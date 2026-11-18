package com.orderprocessing.storeservice.dto;

import com.orderprocessing.storeservice.model.ProductPicture;

import java.time.Instant;
import java.util.UUID;

public record PictureResponse(UUID id, UUID productId, String contentType, Instant createdAt) {
    public static PictureResponse from(ProductPicture picture) {
        return new PictureResponse(picture.getId(), picture.getProductId(), picture.getContentType(),
                picture.getCreatedAt());
    }
}
