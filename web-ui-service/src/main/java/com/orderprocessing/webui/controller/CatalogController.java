package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.PaginationLinks;
import com.orderprocessing.webui.dto.ProductView;
import com.orderprocessing.webui.form.QuantityForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/app/catalog")
public class CatalogController {
    private final AuthenticatedPlatformClient client;
    public CatalogController(AuthenticatedPlatformClient client) { this.client = client; }

    @GetMapping
    public String catalog(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "12") int size,
                          @RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "createdAt,desc") String sort,
                          @RequestParam(defaultValue = "false") boolean inStock,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          Model model) {
        int safeSize = Math.max(1, Math.min(size, 48));
        PageResponse<ProductView> products = client.products(Math.max(0, page), safeSize, q, sort, inStock);
        model.addAttribute("products", products);
        model.addAttribute("q", q); model.addAttribute("sort", sort); model.addAttribute("inStock", inStock);
        Map<String, Object> activeFilters = new LinkedHashMap<>();
        activeFilters.put("q", q);
        activeFilters.put("sort", sort);
        if (inStock) activeFilters.put("inStock", true);
        if (safeSize != 12) activeFilters.put("size", safeSize);
        model.addAttribute("catalogPagination",
                PaginationLinks.forPage(products, "/app/catalog", "page", activeFilters));
        return "true".equalsIgnoreCase(htmx) ? "catalog/_product-grid" : "catalog/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("product", client.product(id));
        model.addAttribute("quantityForm", new QuantityForm());
        return "catalog/detail";
    }
}
