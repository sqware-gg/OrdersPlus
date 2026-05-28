package dev.ordersplus.command;

import dev.ordersplus.order.OrderActionResult;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class OrdersPlusCommand implements CommandExecutor, TabCompleter {
    private final OrderService service;

    public OrdersPlusCommand(OrderService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ordersplus.admin")) {
            message(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            message(sender, "usage-admin", Map.of());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats", "status" -> message(sender, "status", Map.of(
                    "active", Integer.toString(service.activeCount()),
                    "total", Integer.toString(service.totalOrders()),
                    "economy", service.economyName()
            ));
            case "reload" -> {
                service.reload();
                message(sender, "reloaded", Map.of());
            }
            case "save" -> {
                service.save();
                message(sender, "saved", Map.of());
            }
            case "cancel" -> cancel(sender, args);
            default -> message(sender, "usage-admin", Map.of());
        }
        return true;
    }

    private void cancel(CommandSender sender, String[] args) {
        if (args.length != 2) {
            message(sender, "usage-admin", Map.of());
            return;
        }
        Long orderId = parseLong(args[1]);
        if (orderId == null) {
            message(sender, "order-not-found", Map.of());
            return;
        }
        OrderActionResult result = service.cancel(orderId, null, true);
        message(sender, result.messageKey(), result.placeholders());
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void message(CommandSender sender, String key, Map<String, String> placeholders) {
        String rendered = Text.color(service.config().prefix()
                + Text.render(service.config().message(key), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ordersplus.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("stats", "reload", "save", "cancel").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
