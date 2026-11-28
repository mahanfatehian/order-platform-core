package com.orderprocessing.webui.shop;

import java.util.List;

public final class PublicCatalog {
    public List<PublicProduct> published(List<PublicProduct> products) {
        return products.stream().filter(PublicProduct::active).toList();
    }

    public String page(PublicProduct product) {
        if (product == null || !product.active()) {
            throw new IllegalArgumentException("An inactive product is not public");
        }
        return product.name() + "\n" + product.price() + "\n" + new OperatorCopy(null, null).title()
                + "\nThe operator has not set a public domain.";
    }

    public String robots() {
        return """
                # The operator fills this file before attaching a public domain.
                User-agent: *
                Disallow: /
                """;
    }
}
