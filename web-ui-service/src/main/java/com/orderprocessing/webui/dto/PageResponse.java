package com.orderprocessing.webui.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
    }

    public int number() { return page; }
    public boolean hasPrevious() { return page > 0; }

    /**
     * Compared as longs on purpose. The page number is echoed back from the backend, which bounds it below at
     * zero but not above, so a request for page 2147483647 made {@code page + 1} wrap to a negative int - which
     * is less than any page count, so the view offered a Next link past the end of an empty result.
     */
    public boolean hasNext() { return (long) page + 1L < (long) totalPages; }
}
