package dev.ordersplus.order;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrderStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private long nextOrderId = 1L;

    public OrderStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        reload();
    }

    public synchronized void reload() {
        orders.clear();
        nextOrderId = 1L;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextOrderId = Math.max(1L, yaml.getLong("meta.next-order-id", 1L));
        loadOrders(yaml.getConfigurationSection("orders"));
        for (Long id : orders.keySet()) {
            nextOrderId = Math.max(nextOrderId, id + 1L);
        }
    }

    private void loadOrders(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection orderSection = section.getConfigurationSection(key);
            if (orderSection == null) {
                continue;
            }
            try {
                long id = Long.parseLong(key);
                UUID buyerUuid = UUID.fromString(orderSection.getString("buyer.uuid", ""));
                Material material = Material.matchMaterial(orderSection.getString("material", ""));
                if (material == null || !material.isItem() || material.isAir()) {
                    plugin.getLogger().warning("Ignoring order " + key + " because it has an invalid material.");
                    continue;
                }
                int fulfilledAmount = orderSection.getInt("fulfilled-amount", 0);
                Order order = new Order(
                        id,
                        buyerUuid,
                        orderSection.getString("buyer.name", ""),
                        material,
                        orderSection.getInt("original-amount", 0),
                        orderSection.getInt("remaining-amount", 0),
                        orderSection.getDouble("price-each", 0.0D),
                        orderSection.getLong("created-at", 0L),
                        orderSection.getLong("expires-at", 0L),
                        parseStatus(orderSection.getString("status", "ACTIVE")),
                        fulfilledAmount,
                        orderSection.getInt("claimable-amount", fulfilledAmount)
                );
                orders.put(id, order);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid order " + key + ": " + e.getMessage());
            }
        }
    }

    public synchronized Order createOrder(UUID buyerUuid, String buyerName, Material material, int amount,
                                          double priceEach, long createdAtMillis, long expiresAtMillis) {
        Order order = new Order(nextOrderId++, buyerUuid, buyerName, material, amount, amount, priceEach,
                createdAtMillis, expiresAtMillis, OrderStatus.ACTIVE, 0, 0);
        orders.put(order.id(), order);
        return order;
    }

    public synchronized Optional<Order> order(long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public synchronized void remove(long id) {
        orders.remove(id);
    }

    public synchronized Collection<Order> orders() {
        return new ArrayList<>(orders.values());
    }

    public synchronized int totalOrders() {
        return orders.size();
    }

    public synchronized boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("meta.next-order-id", nextOrderId);
        for (Order order : orders.values()) {
            String path = "orders." + order.id();
            yaml.set(path + ".buyer.uuid", order.buyerUuid().toString());
            yaml.set(path + ".buyer.name", order.buyerName());
            yaml.set(path + ".material", order.material().name());
            yaml.set(path + ".original-amount", order.originalAmount());
            yaml.set(path + ".remaining-amount", order.remainingAmount());
            yaml.set(path + ".fulfilled-amount", order.fulfilledAmount());
            yaml.set(path + ".claimable-amount", order.claimableAmount());
            yaml.set(path + ".price-each", order.priceEach());
            yaml.set(path + ".created-at", order.createdAtMillis());
            yaml.set(path + ".expires-at", order.expiresAtMillis());
            yaml.set(path + ".status", order.status().name());
        }
        Path target = file.toPath();
        Path directory = target.getParent();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path temp = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = Files.createTempFile(directory, file.getName(), ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save orders.yml: " + e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return OrderStatus.ACTIVE;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return OrderStatus.ACTIVE;
        }
    }
}
