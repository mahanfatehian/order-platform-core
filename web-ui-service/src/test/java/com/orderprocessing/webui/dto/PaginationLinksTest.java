package com.orderprocessing.webui.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationLinksTest {

    @Test
    void preservesAndEncodesActiveFiltersInBothDirections() {
        PageResponse<String> page = new PageResponse<>(List.of("result"), 2, 12, 48, 4, false, false);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("q", "Signal Lamp & Shade");
        filters.put("sort", "price,desc");
        filters.put("inStock", true);
        filters.put("unused", "");

        PaginationLinks links = PaginationLinks.forPage(page, "/app/catalog", "page", filters);

        assertEquals("/app/catalog?q=Signal%20Lamp%20%26%20Shade&sort=price,desc&inStock=true&page=1",
                links.previousUrl());
        assertEquals("/app/catalog?q=Signal%20Lamp%20%26%20Shade&sort=price,desc&inStock=true&page=3",
                links.nextUrl());
    }

    @Test
    void representsUnavailableDirectionsWithoutSyntheticLinks() {
        PageResponse<String> firstPage = new PageResponse<>(List.of(), 0, 20, 40, 2, true, false);
        PageResponse<String> lastPage = new PageResponse<>(List.of(), 1, 20, 40, 2, false, true);

        assertNull(PaginationLinks.forPage(firstPage, "/items", "page", Map.of()).previousUrl());
        assertNull(PaginationLinks.forPage(lastPage, "/items", "page", Map.of()).nextUrl());
    }

    @Test
    void rejectsAnEmptyPageParameterName() {
        PageResponse<String> page = PageResponse.empty(0, 20);

        assertThrows(IllegalArgumentException.class,
                () -> PaginationLinks.forPage(page, "/items", " ", Map.of()));
    }
}
