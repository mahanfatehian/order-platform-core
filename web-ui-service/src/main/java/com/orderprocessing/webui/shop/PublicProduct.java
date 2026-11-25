package com.orderprocessing.webui.shop;

import java.util.UUID;

public record PublicProduct(UUID id, String name, String price, boolean active) {
}
