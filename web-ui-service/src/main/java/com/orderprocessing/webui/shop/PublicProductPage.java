package com.orderprocessing.webui.shop;

public final class PublicProductPage {
    private final PublicCatalog catalog = new PublicCatalog();

    public String render(PublicProduct product) {
        String page = catalog.page(product);
        if (page.toLowerCase().contains("guest checkout")) {
            throw new IllegalStateException("A public page must not offer guest checkout");
        }
        return page;
    }
}
