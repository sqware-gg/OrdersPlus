package dev.ordersplus.order;

import java.util.Map;

public record OrderActionResult(boolean success, String messageKey, Map<String, String> placeholders) {
    public static OrderActionResult success(String key, Map<String, String> placeholders) {
        return new OrderActionResult(true, key, placeholders);
    }

    public static OrderActionResult failure(String key, Map<String, String> placeholders) {
        return new OrderActionResult(false, key, placeholders);
    }
}
