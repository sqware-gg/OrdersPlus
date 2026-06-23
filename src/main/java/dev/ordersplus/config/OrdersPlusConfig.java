package dev.ordersplus.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrdersPlusConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration defaultConfig;
    private Set<Material> blacklistedMaterials = EnumSet.noneOf(Material.class);
    private Map<String, Integer> activeLimitByRank = Map.of();

    public OrdersPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        defaultConfig = loadBundledConfig();
        activeLimitByRank = rankLimits("orders.rank-limits");
        blacklistedMaterials = EnumSet.noneOf(Material.class);
        for (String materialName : config.getStringList("orders.blacklisted-materials")) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Ignoring invalid blacklisted material: " + materialName);
                continue;
            }
            blacklistedMaterials.add(material);
        }
    }

    public double minPriceEach() {
        return nonNegativeDouble("economy.min-price-each", 1.0D);
    }

    public double maxPriceEach() {
        return nonNegativeDouble("economy.max-price-each", 1_000_000_000.0D);
    }

    public double maxOrderTotal() {
        return nonNegativeDouble("economy.max-order-total", 1_000_000_000.0D);
    }

    public boolean allowFulfillOwnOrders() {
        return config.getBoolean("economy.allow-fulfill-own-orders", false);
    }

    public boolean allowNonStackableItems() {
        return config.getBoolean("orders.allow-non-stackable-items", false);
    }

    public boolean requirePlainItems() {
        return config.getBoolean("orders.require-plain-items", true);
    }

    public long defaultDurationMillis() {
        return safeMultiply(Math.max(1L, config.getLong("orders.default-duration-hours", 72L)), 3_600_000L);
    }

    public long maxDurationMillis() {
        return safeMultiply(Math.max(1L, config.getLong("orders.max-duration-hours", 168L)), 3_600_000L);
    }

    public int maxActivePerPlayer() {
        return Math.max(1, config.getInt("orders.max-active-per-player", 20));
    }

    public Map<String, Integer> activeLimitByRank() {
        return activeLimitByRank;
    }

    public int maxAmount() {
        return Math.max(1, config.getInt("orders.max-amount", 3456));
    }

    public long expireCheckTicks() {
        return Math.max(20L, safeMultiply(Math.max(1L, config.getLong("orders.expire-check-seconds", 60L)), 20L));
    }

    public long saveIntervalTicks() {
        return Math.max(20L, safeMultiply(Math.max(1L, config.getLong("orders.save-interval-seconds", 300L)), 20L));
    }

    public boolean blacklisted(Material material) {
        return blacklistedMaterials.contains(material);
    }

    public boolean orderCreatedAnnouncementEnabled() {
        return config.getBoolean("announcements.order-created.enabled", true);
    }

    public boolean orderCreatedAnnouncementIncludeBuyer() {
        return config.getBoolean("announcements.order-created.include-buyer", false);
    }

    public String orderCreatedAnnouncementRecipientPermission() {
        return config.getString("announcements.order-created.recipient-permission", "ordersplus.announce.receive");
    }

    public String orderCreatedAnnouncementClickAction() {
        return config.getString("announcements.order-created.click-action", "suggest");
    }

    public String orderCreatedAnnouncementClickCommand() {
        return config.getString("announcements.order-created.click-command", "/orders fulfill {id}");
    }

    public String orderCreatedAnnouncementFormat() {
        return config.getString("announcements.order-created.format",
                "&#2b98fdOrders &8> &f{buyer} &7created order &f#{id}&7: &f{amount}x {material}&7 at &#57F287{price_each}&7 each.");
    }

    public String orderCreatedAnnouncementHover() {
        return config.getString("announcements.order-created.hover",
                "&7Click to fulfil order &f#{id}&7.\n&7Total escrow: &f{total}&7.\n&7Expires in: &f{duration}&7.");
    }

    public String guiTitle(String key) {
        return config.getString("gui." + key.toLowerCase(Locale.ROOT), "");
    }

    public String prefix() {
        return message("prefix");
    }

    public String message(String key) {
        String path = "messages." + key;
        String message = config.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        message = defaultConfig.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Missing message: " + key;
    }

    private FileConfiguration loadBundledConfig() {
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled config defaults: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private double nonNegativeDouble(String path, double fallback) {
        double value = config.getDouble(path, fallback);
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }

    private Map<String, Integer> rankLimits(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String rank : section.getKeys(false)) {
            if (rank == null || rank.isBlank()) {
                continue;
            }
            int limit = section.getInt(rank, -1);
            if (limit <= 0) {
                plugin.getLogger().warning("Ignoring invalid rank limit at " + path + "." + rank + ": " + limit);
                continue;
            }
            limits.put(rank.toLowerCase(Locale.ROOT), limit);
        }
        return Collections.unmodifiableMap(limits);
    }

    private long safeMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }
}
