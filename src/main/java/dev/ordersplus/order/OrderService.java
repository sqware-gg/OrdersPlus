package dev.ordersplus.order;

import dev.ordersplus.api.event.OrderCancelledEvent;
import dev.ordersplus.api.event.OrderCreatedEvent;
import dev.ordersplus.api.event.OrderExpiredEvent;
import dev.ordersplus.api.event.OrderFulfilledEvent;
import dev.ordersplus.config.OrdersPlusConfig;
import dev.ordersplus.economy.EconomyService;
import dev.ordersplus.util.DurationFormatter;
import dev.ordersplus.util.InventoryUtil;
import dev.ordersplus.util.MaterialNames;
import dev.ordersplus.util.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OrderService {
    private final JavaPlugin plugin;
    private final OrdersPlusConfig config;
    private final OrderStore store;
    private final EconomyService economy;
    private BukkitTask expireTask;
    private BukkitTask saveTask;

    public OrderService(JavaPlugin plugin, OrdersPlusConfig config, OrderStore store, EconomyService economy) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.economy = economy;
    }

    public void start() {
        expireTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expireOrders,
                config.expireCheckTicks(), config.expireCheckTicks());
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, store::save,
                config.saveIntervalTicks(), config.saveIntervalTicks());
    }

    public void stop() {
        if (expireTask != null) {
            expireTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        store.save();
    }

    public void reload() {
        config.reload();
        economy.refresh();
        if (expireTask != null) {
            expireTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        start();
    }

    public OrdersPlusConfig config() {
        return config;
    }

    public synchronized OrderActionResult create(Player buyer, Material material, int amount, double priceEach,
                                                 long durationMillis) {
        if (!economy.available()) {
            return OrderActionResult.failure("economy-unavailable", Map.of());
        }
        if (material == null || !material.isItem() || material.isAir()) {
            return OrderActionResult.failure("invalid-material", Map.of());
        }
        if (config.blacklisted(material)) {
            return OrderActionResult.failure("blacklisted-material", Map.of());
        }
        if (!config.allowNonStackableItems() && material.getMaxStackSize() <= 1) {
            return OrderActionResult.failure("non-stackable-material", Map.of());
        }
        if (amount <= 0 || amount > config.maxAmount()) {
            return OrderActionResult.failure("invalid-amount", Map.of("max", Integer.toString(config.maxAmount())));
        }
        if (!validPrice(priceEach)) {
            return OrderActionResult.failure("invalid-price", Map.of(
                    "min", economy.format(config.minPriceEach()),
                    "max", config.maxPriceEach() <= 0.0D ? "unlimited" : economy.format(config.maxPriceEach())
            ));
        }
        if (!buyer.hasPermission("ordersplus.limit.bypass")
                && activeOrders(buyer.getUniqueId()).size() >= config.maxActivePerPlayer()) {
            return OrderActionResult.failure("order-limit", Map.of("limit", Integer.toString(config.maxActivePerPlayer())));
        }

        double total = amount * priceEach;
        if (!Double.isFinite(total) || (config.maxOrderTotal() > 0.0D && total > config.maxOrderTotal())) {
            return OrderActionResult.failure("order-total-too-high", Map.of("max_total", economy.format(config.maxOrderTotal())));
        }
        if (!economy.has(buyer, total)) {
            return OrderActionResult.failure("not-enough-money", Map.of("price", economy.format(total)));
        }
        EconomyResponse withdraw = economy.withdraw(buyer, total);
        if (!withdraw.transactionSuccess()) {
            return OrderActionResult.failure("transaction-failed", Map.of("reason", errorMessage(withdraw)));
        }

        long duration = Math.min(Math.max(1000L, durationMillis), config.maxDurationMillis());
        long now = System.currentTimeMillis();
        Order order = store.createOrder(buyer.getUniqueId(), buyer.getName(), material, amount, priceEach,
                now, safeExpiresAt(now, duration));
        if (!store.save()) {
            store.remove(order.id());
            EconomyResponse refund = economy.deposit(buyer, total);
            if (!refund.transactionSuccess()) {
                plugin.getLogger().warning("Could not refund failed order creation for "
                        + buyer.getName() + ": " + errorMessage(refund));
            }
            return OrderActionResult.failure("storage-failed", Map.of());
        }
        Bukkit.getPluginManager().callEvent(new OrderCreatedEvent(
                order.id(),
                order.buyerUuid(),
                order.buyerName(),
                order.material(),
                MaterialNames.display(order.material()),
                amount,
                priceEach,
                economy.format(priceEach),
                total,
                economy.format(total),
                order.createdAtMillis(),
                order.expiresAtMillis()
        ));
        return OrderActionResult.success("order-created", Map.of(
                "id", Long.toString(order.id()),
                "amount", Integer.toString(amount),
                "material", MaterialNames.display(material),
                "price_each", economy.format(priceEach),
                "total", economy.format(total),
                "duration", DurationFormatter.compact(duration)
        ));
    }

    public synchronized OrderActionResult fulfill(long orderId, Player fulfiller, int requestedAmount) {
        if (!economy.available()) {
            return OrderActionResult.failure("economy-unavailable", Map.of());
        }
        if (requestedAmount <= 0) {
            return OrderActionResult.failure("invalid-amount", Map.of("max", Integer.toString(config.maxAmount())));
        }
        Optional<Order> optionalOrder = store.order(orderId);
        if (optionalOrder.isEmpty()) {
            return OrderActionResult.failure("order-not-found", Map.of());
        }
        Order order = optionalOrder.get();
        long now = System.currentTimeMillis();
        if (order.expired(now)) {
            expireOrder(order);
            store.save();
            return OrderActionResult.failure("order-expired", Map.of());
        }
        if (!order.active()) {
            return OrderActionResult.failure("order-unavailable", Map.of());
        }
        if (!config.allowFulfillOwnOrders() && order.buyerUuid().equals(fulfiller.getUniqueId())) {
            return OrderActionResult.failure("cannot-fulfill-own", Map.of());
        }

        boolean requirePlainItems = config.requirePlainItems();
        int available = InventoryUtil.count(fulfiller, order.material(), requirePlainItems);
        int fillAmount = Math.min(order.remainingAmount(), Math.min(Math.max(1, requestedAmount), available));
        if (fillAmount <= 0) {
            if (requirePlainItems && InventoryUtil.totalCount(fulfiller, order.material()) > 0) {
                return OrderActionResult.failure("no-plain-items", Map.of("material", MaterialNames.display(order.material())));
            }
            return OrderActionResult.failure("no-matching-items", Map.of("material", MaterialNames.display(order.material())));
        }

        double payout = fillAmount * order.priceEach();
        if (!InventoryUtil.remove(fulfiller, order.material(), fillAmount, requirePlainItems)) {
            return OrderActionResult.failure("no-matching-items", Map.of("material", MaterialNames.display(order.material())));
        }

        order.fill(fillAmount);
        if (!store.save()) {
            order.undoFill(fillAmount);
            store.save();
            InventoryUtil.returnItems(fulfiller, order.material(), fillAmount);
            return OrderActionResult.failure("storage-failed", Map.of());
        }
        EconomyResponse deposit = economy.deposit(fulfiller, payout);
        if (!deposit.transactionSuccess()) {
            order.undoFill(fillAmount);
            if (store.save()) {
                InventoryUtil.returnItems(fulfiller, order.material(), fillAmount);
            } else {
                plugin.getLogger().warning("Could not roll back failed fulfilment for order #" + order.id()
                        + "; items were not returned to avoid duplication.");
            }
            return OrderActionResult.failure("transaction-failed", Map.of("reason", errorMessage(deposit)));
        }

        Player buyer = Bukkit.getPlayer(order.buyerUuid());
        if (buyer != null && buyer.isOnline()) {
            send(buyer, "order-filled-buyer", Map.of(
                    "id", Long.toString(order.id()),
                    "player", fulfiller.getName(),
                    "amount", Integer.toString(fillAmount),
                    "material", MaterialNames.display(order.material())
            ));
        }
        Bukkit.getPluginManager().callEvent(new OrderFulfilledEvent(
                order.id(),
                order.buyerUuid(),
                order.buyerName(),
                fulfiller.getUniqueId(),
                fulfiller.getName(),
                order.material(),
                MaterialNames.display(order.material()),
                fillAmount,
                order.remainingAmount(),
                payout,
                economy.format(payout),
                !order.active(),
                System.currentTimeMillis()
        ));
        return OrderActionResult.success("fulfill-complete", Map.of(
                "id", Long.toString(order.id()),
                "amount", Integer.toString(fillAmount),
                "material", MaterialNames.display(order.material()),
                "payout", economy.format(payout)
        ));
    }

    public synchronized OrderActionResult claim(long orderId, Player buyer, int requestedAmount) {
        Optional<Order> optionalOrder = store.order(orderId);
        if (optionalOrder.isEmpty()) {
            return OrderActionResult.failure("order-not-found", Map.of());
        }
        Order order = optionalOrder.get();
        if (!order.buyerUuid().equals(buyer.getUniqueId())) {
            return OrderActionResult.failure("claim-denied", Map.of());
        }
        ClaimResult result = claimFromOrder(order, buyer, requestedAmount);
        if (result.storageFailed()) {
            return OrderActionResult.failure("storage-failed", Map.of());
        }
        if (result.claimedAmount() <= 0) {
            return OrderActionResult.failure(result.inventoryFull() ? "inventory-full" : "claim-empty", Map.of(
                    "material", MaterialNames.display(order.material())
            ));
        }
        return OrderActionResult.success("claim-complete", Map.of(
                "id", Long.toString(order.id()),
                "amount", Integer.toString(result.claimedAmount()),
                "material", MaterialNames.display(order.material())
        ));
    }

    public synchronized OrderActionResult claimAll(Player buyer) {
        int claimedAmount = 0;
        int claimedOrders = 0;
        boolean inventoryFull = false;
        for (Order order : store.orders().stream()
                .filter(candidate -> candidate.buyerUuid().equals(buyer.getUniqueId()))
                .filter(candidate -> candidate.claimableAmount() > 0)
                .sorted(Comparator.comparingLong(Order::createdAtMillis))
                .toList()) {
            ClaimResult result = claimFromOrder(order, buyer, Integer.MAX_VALUE);
            if (result.storageFailed()) {
                return OrderActionResult.failure("storage-failed", Map.of());
            }
            if (result.claimedAmount() > 0) {
                claimedAmount += result.claimedAmount();
                claimedOrders++;
            }
            if (result.inventoryFull()) {
                inventoryFull = true;
                break;
            }
        }
        if (claimedAmount <= 0) {
            return OrderActionResult.failure(inventoryFull ? "inventory-full" : "claim-empty", Map.of("material", "items"));
        }
        return OrderActionResult.success("claim-all-complete", Map.of(
                "amount", Integer.toString(claimedAmount),
                "orders", Integer.toString(claimedOrders)
        ));
    }

    public synchronized OrderActionResult cancel(long orderId, Player player, boolean admin) {
        if (!economy.available()) {
            return OrderActionResult.failure("economy-unavailable", Map.of());
        }
        Optional<Order> optionalOrder = store.order(orderId);
        if (optionalOrder.isEmpty()) {
            return OrderActionResult.failure("order-not-found", Map.of());
        }
        Order order = optionalOrder.get();
        if (order.expired(System.currentTimeMillis())) {
            expireOrder(order);
            store.save();
            return OrderActionResult.failure("order-expired", Map.of());
        }
        if (!order.active()) {
            return OrderActionResult.failure("order-unavailable", Map.of());
        }
        if (!admin && (player == null || !order.buyerUuid().equals(player.getUniqueId()))) {
            return OrderActionResult.failure("cancel-denied", Map.of());
        }
        double refund = order.remainingValue();
        order.markCancelled();
        if (!store.save()) {
            order.markActive();
            return OrderActionResult.failure("storage-failed", Map.of());
        }
        EconomyResponse deposit = economy.deposit(Bukkit.getOfflinePlayer(order.buyerUuid()), refund);
        if (!deposit.transactionSuccess()) {
            order.markActive();
            store.save();
            plugin.getLogger().warning("Could not refund cancelled order #" + order.id() + ": " + errorMessage(deposit));
            return OrderActionResult.failure("transaction-failed", Map.of("reason", errorMessage(deposit)));
        }
        Bukkit.getPluginManager().callEvent(new OrderCancelledEvent(
                order.id(),
                order.buyerUuid(),
                order.buyerName(),
                player == null ? "Staff" : player.getName(),
                order.material(),
                MaterialNames.display(order.material()),
                order.remainingAmount(),
                refund,
                economy.format(refund),
                System.currentTimeMillis()
        ));
        return OrderActionResult.success("cancel-complete", Map.of(
                "id", Long.toString(order.id()),
                "refund", economy.format(refund)
        ));
    }

    public synchronized void expireOrders() {
        boolean changed = false;
        for (Order order : store.orders()) {
            if (order.expired(System.currentTimeMillis())) {
                expireOrder(order);
                changed = true;
            }
        }
        if (changed) {
            store.save();
        }
    }

    private void expireOrder(Order order) {
        if (!order.active()) {
            return;
        }
        if (!economy.available()) {
            plugin.getLogger().warning("Could not refund expired order #" + order.id() + ": no economy provider is available.");
            return;
        }
        double refund = order.remainingValue();
        order.markExpired();
        if (!store.save()) {
            order.markActive();
            plugin.getLogger().warning("Could not expire order #" + order.id() + ": storage failed before refund.");
            return;
        }
        EconomyResponse deposit = economy.deposit(Bukkit.getOfflinePlayer(order.buyerUuid()), refund);
        if (!deposit.transactionSuccess()) {
            order.markActive();
            store.save();
            plugin.getLogger().warning("Could not refund expired order #" + order.id() + ": " + errorMessage(deposit));
            return;
        }
        Player buyer = Bukkit.getPlayer(order.buyerUuid());
        if (buyer != null && buyer.isOnline()) {
            send(buyer, "order-expired-refund", Map.of(
                    "id", Long.toString(order.id()),
                    "refund", economy.format(refund)
            ));
        }
        Bukkit.getPluginManager().callEvent(new OrderExpiredEvent(
                order.id(),
                order.buyerUuid(),
                order.buyerName(),
                order.material(),
                MaterialNames.display(order.material()),
                order.remainingAmount(),
                refund,
                economy.format(refund),
                System.currentTimeMillis()
        ));
    }

    public synchronized List<Order> browseOrders(String search) {
        expireOrders();
        return store.orders().stream()
                .filter(Order::active)
                .filter(order -> order.matches(search))
                .sorted(Comparator.comparingLong(Order::createdAtMillis).reversed())
                .toList();
    }

    public synchronized List<Order> activeOrders(UUID buyerUuid) {
        expireOrders();
        return store.orders().stream()
                .filter(Order::active)
                .filter(order -> order.buyerUuid().equals(buyerUuid))
                .sorted(Comparator.comparingLong(Order::createdAtMillis).reversed())
                .toList();
    }

    public synchronized List<Order> manageableOrders(UUID buyerUuid) {
        expireOrders();
        return store.orders().stream()
                .filter(order -> order.buyerUuid().equals(buyerUuid))
                .filter(order -> order.active() || order.claimableAmount() > 0)
                .sorted(Comparator.comparingLong(Order::createdAtMillis).reversed())
                .toList();
    }

    public synchronized int activeCount() {
        expireOrders();
        return (int) store.orders().stream().filter(Order::active).count();
    }

    public int totalOrders() {
        return store.totalOrders();
    }

    public void save() {
        store.save();
    }

    public String formatMoney(double amount) {
        return economy.format(amount);
    }

    public String economyName() {
        return economy.providerName();
    }

    private boolean validPrice(double price) {
        if (!Double.isFinite(price) || price < config.minPriceEach()) {
            return false;
        }
        return config.maxPriceEach() <= 0.0D || price <= config.maxPriceEach();
    }

    private long safeExpiresAt(long now, long duration) {
        long expiresAt = now + duration;
        return expiresAt < now ? Long.MAX_VALUE : expiresAt;
    }

    private String errorMessage(EconomyResponse response) {
        if (response == null || response.errorMessage == null || response.errorMessage.isBlank()) {
            return "unknown";
        }
        return response.errorMessage;
    }

    private ClaimResult claimFromOrder(Order order, Player buyer, int requestedAmount) {
        int requested = requestedAmount <= 0 ? Integer.MAX_VALUE : requestedAmount;
        int space = InventoryUtil.spaceFor(buyer, order.material(), config.requirePlainItems());
        int claimAmount = Math.min(order.claimableAmount(), Math.min(requested, space));
        if (claimAmount <= 0) {
            return new ClaimResult(0, order.claimableAmount() > 0 && space <= 0, false);
        }
        order.claim(claimAmount);
        if (!store.save()) {
            order.restoreClaimable(claimAmount);
            store.save();
            return new ClaimResult(0, false, true);
        }
        int leftover = InventoryUtil.give(buyer, order.material(), claimAmount);
        if (leftover > 0) {
            order.restoreClaimable(leftover);
            store.save();
            claimAmount -= leftover;
        }
        return new ClaimResult(Math.max(0, claimAmount), leftover > 0, false);
    }

    private record ClaimResult(int claimedAmount, boolean inventoryFull, boolean storageFailed) {
    }

    private void send(Player player, String messageKey, Map<String, String> placeholders) {
        String rendered = Text.color(config.prefix() + Text.render(config.message(messageKey), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            player.sendMessage(line);
        }
    }
}
