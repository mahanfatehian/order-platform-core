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
    public boolean hasNext() { return page + 1 < totalPages; }
}
