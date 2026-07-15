package com.orderprocessing.webui.dto;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

/**
 * Pre-encoded pagination targets for a page of results.
 *
 * <p>Building these links on the server keeps active filters intact and avoids
 * assembling query strings in Thymeleaf. A missing target represents a
 * disabled direction and is intentionally rendered as text rather than a link.</p>
 */
public record PaginationLinks(String previousUrl, String nextUrl) {

    public static PaginationLinks forPage(PageResponse<?> page,
                                          String basePath,
                                          String pageParameter,
                                          Map<String, ?> preservedParameters) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(basePath, "basePath");
        if (pageParameter == null || pageParameter.isBlank()) {
            throw new IllegalArgumentException("pageParameter must not be blank");
        }

        Map<String, ?> parameters = preservedParameters == null ? Map.of() : preservedParameters;
        String previous = page.hasPrevious()
                ? buildUrl(basePath, pageParameter, page.number() - 1, parameters)
                : null;
        String next = page.hasNext()
                ? buildUrl(basePath, pageParameter, page.number() + 1, parameters)
                : null;
        return new PaginationLinks(previous, next);
    }

    private static String buildUrl(String basePath,
                                   String pageParameter,
                                   int targetPage,
                                   Map<String, ?> preservedParameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);
        preservedParameters.forEach((name, value) -> {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                builder.queryParam(name, value);
            }
        });
        return builder.queryParam(pageParameter, Math.max(targetPage, 0))
                .build()
                .encode()
                .toUriString();
    }
}
