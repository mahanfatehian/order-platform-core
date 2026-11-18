package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.exception.ResourceNotFoundException;
import com.orderprocessing.storeservice.model.ProductPicture;
import com.orderprocessing.storeservice.repository.ProductPictureRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductPictureService {
    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";

    private final ProductRepository products;
    private final ProductPictureRepository pictures;

    public ProductPictureService(ProductRepository products, ProductPictureRepository pictures) {
        this.products = products;
        this.pictures = pictures;
    }

    @Transactional
    public ProductPicture store(UUID productId, String contentType, byte[] bytes) {
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }
        String type = mediaType(contentType);
        if (!PNG.equals(type) && !JPEG.equals(type)) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA",
                    "Only image/png and image/jpeg are accepted");
        }
        if (bytes == null || bytes.length == 0) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "EMPTY_PICTURE", "Picture bytes are required");
        }
        ProductPicture picture = new ProductPicture();
        picture.setId(UUID.randomUUID());
        picture.setProductId(productId);
        picture.setContentType(type);
        picture.setBytes(bytes);
        picture.setCreatedAt(Instant.now());
        return pictures.save(picture);
    }

    @Transactional(readOnly = true)
    public ProductPicture load(UUID productId, UUID pictureId) {
        return pictures.findByIdAndProductId(pictureId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Picture not found"));
    }

    static String mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String type = contentType;
        int separator = type.indexOf(';');
        if (separator >= 0) {
            type = type.substring(0, separator);
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
