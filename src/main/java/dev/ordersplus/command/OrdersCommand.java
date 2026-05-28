package dev.ordersplus.command;

import dev.ordersplus.gui.OrdersGui;
import dev.ordersplus.gui.MaterialSearchPrompt;
import dev.ordersplus.order.OrderActionResult;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.DurationParser;
import dev.ordersplus.util.MaterialNames;
import dev.ordersplus.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class OrdersCommand implements CommandExecutor, TabCompleter {
    private final OrderService service;
    private final OrdersGui gui;
    private final MaterialSearchPrompt materialSearchPrompt;

    public OrdersCommand(OrderService service, OrdersGui gui, MaterialSearchPrompt materialSearchPrompt) {
        this.service = service;
        this.gui = gui;
        this.materialSearchPrompt = materialSearchPrompt;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "players-only", Map.of());
            return true;
        }
        if (!player.hasPermission("ordersplus.use")) {
            message(player, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            gui.openBrowse(player, null, 0);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "search", "find" -> search(player, args);
            case "items", "item", "materials", "material" -> materialSearch(player, args);
            case "create", "buy" -> create(player, args);
            case "fulfill", "fulfil", "fill" -> fulfill(player, args);
            case "claim", "collect" -> claim(player, args);
            case "manage", "active", "mine" -> gui.openManage(player, 0);
            case "cancel" -> cancel(player, args);
            case "help", "commands", "?" -> message(player, "help", Map.of());
            default -> message(player, "help", Map.of());
        }
        return true;
    }

    private void search(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.search")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            message(player, "usage-search", Map.of());
            return;
        }
        gui.openBrowse(player, String.join(" ", List.of(args).subList(1, args.length)), 0);
    }

    private void materialSearch(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.search")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            materialSearchPrompt.open(player);
            return;
        }
        gui.openMaterialSearch(player, String.join(" ", List.of(args).subList(1, args.length)), 0);
    }

    private void create(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.create")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 4 || args.length > 5) {
            message(player, "usage-create", Map.of());
            return;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null || !material.isItem() || material.isAir()) {
            message(player, "invalid-material", Map.of());
            return;
        }
        Integer amount = parseInt(args[2]);
        if (amount == null) {
            message(player, "invalid-amount", Map.of("max", Integer.toString(service.config().maxAmount())));
            return;
        }
        Double priceEach = parseDouble(args[3]);
        if (priceEach == null) {
            message(player, "invalid-price", Map.of(
                    "min", service.formatMoney(service.config().minPriceEach()),
                    "max", service.config().maxPriceEach() <= 0.0D ? "unlimited" : service.formatMoney(service.config().maxPriceEach())
            ));
            return;
        }
        long duration = service.config().defaultDurationMillis();
        if (args.length == 5) {
            OptionalLong parsedDuration = DurationParser.parseMillis(args[4]);
            if (parsedDuration.isEmpty()) {
                message(player, "invalid-duration", Map.of());
                return;
            }
            duration = parsedDuration.getAsLong();
        }
        send(player, service.create(player, material, amount, priceEach, duration));
    }

    private void fulfill(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.fulfill")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2 || args.length > 3) {
            message(player, "usage-fulfill", Map.of());
            return;
        }
        Long orderId = parseLong(args[1]);
        if (orderId == null) {
            message(player, "order-not-found", Map.of());
            return;
        }
        int amount = Integer.MAX_VALUE;
        if (args.length == 3) {
            Integer parsedAmount = parseInt(args[2]);
            if (parsedAmount == null || parsedAmount <= 0) {
                message(player, "invalid-amount", Map.of("max", Integer.toString(service.config().maxAmount())));
                return;
            }
            amount = parsedAmount;
        }
        send(player, service.fulfill(orderId, player, amount));
    }

    private void cancel(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.cancel")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length != 2) {
            message(player, "usage-cancel", Map.of());
            return;
        }
        Long orderId = parseLong(args[1]);
        if (orderId == null) {
            message(player, "order-not-found", Map.of());
            return;
        }
        send(player, service.cancel(orderId, player, false));
    }

    private void claim(Player player, String[] args) {
        if (!player.hasPermission("ordersplus.use")) {
            message(player, "no-permission", Map.of());
            return;
        }
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("all"))) {
            send(player, service.claimAll(player));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            message(player, "usage-claim", Map.of());
            return;
        }
        Long orderId = parseLong(args[1]);
        if (orderId == null) {
            message(player, "order-not-found", Map.of());
            return;
        }
        int amount = Integer.MAX_VALUE;
        if (args.length == 3) {
            Integer parsedAmount = parseInt(args[2]);
            if (parsedAmount == null || parsedAmount <= 0) {
                message(player, "invalid-amount", Map.of("max", Integer.toString(service.config().maxAmount())));
                return;
            }
            amount = parsedAmount;
        }
        send(player, service.claim(orderId, player, amount));
    }

    private Integer parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void send(CommandSender sender, OrderActionResult result) {
        message(sender, result.messageKey(), result.placeholders());
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
        if (!(sender instanceof Player) || !sender.hasPermission("ordersplus.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("search", "items", "create", "fulfill", "claim", "manage", "cancel", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(MaterialNames.searchItems(args[1], 30).stream()
                    .filter(material -> service.config().allowNonStackableItems() || material.getMaxStackSize() > 1)
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("64", "128", "256", "3456"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("10", "100", "1000"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("2h", "12h", "1d", "3d", "7d"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return filter(List.of("all"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }
}
