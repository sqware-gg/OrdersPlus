package dev.ordersplus.api;

import dev.ordersplus.order.Order;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.MaterialNames;
import java.util.List;

public final class OrdersPlusApi {
    private static OrderService service;

    private OrdersPlusApi() {
    }

    public static void register(OrderService orderService) {
        service = orderService;
    }

    public static void unregister() {
        service = null;
    }

    public static List<OrderView> activeOrders(int limit, String search) {
        if (service == null) {
            return List.of();
        }
        int cappedLimit = Math.max(1, Math.min(25, limit));
        return service.browseOrders(search).stream()
                .limit(cappedLimit)
                .map(OrdersPlusApi::view)
                .toList();
    }

    private static OrderView view(Order order) {
        return new OrderView(
                order.id(),
                order.buyerName(),
                MaterialNames.display(order.material()),
                order.material().name().toLowerCase(java.util.Locale.ROOT),
                order.originalAmount(),
                order.remainingAmount(),
                order.fulfilledAmount(),
                order.claimableAmount(),
                order.priceEach(),
                service.formatMoney(order.priceEach()),
                order.remainingValue(),
                service.formatMoney(order.remainingValue()),
                order.status().name().toLowerCase(java.util.Locale.ROOT),
                order.createdAtMillis(),
                order.expiresAtMillis()
        );
    }
}
