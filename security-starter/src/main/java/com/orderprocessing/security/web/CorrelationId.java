package com.orderprocessing.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationId.class.getName();
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private CorrelationId() {
    }

    public static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String provided = request.getHeader(HEADER);
        String value = provided != null && SAFE_VALUE.matcher(provided).matches()
                ? provided
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, value);
        return value;
    }
}
