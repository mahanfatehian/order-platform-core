package com.orderprocessing.webui.shop;

public record OperatorCopy(String title, String description) {
    public OperatorCopy {
        if (claimsRanking(title) || claimsRanking(description)) {
            throw new IllegalArgumentException("The operator page does not claim a ranking");
        }
        title = blank(title) ? "Shop" : title.trim();
        description = blank(description) ? "The operator fills this description." : description.trim();
    }

    private static boolean claimsRanking(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("ranking") || lower.contains("number one") || lower.contains("#1");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
