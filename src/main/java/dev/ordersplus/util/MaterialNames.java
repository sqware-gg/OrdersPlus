package dev.ordersplus.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

public final class MaterialNames {
    private MaterialNames() {
    }

    public static String display(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public static List<Material> searchItems(String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        List<Material> matches = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || material.isAir()) {
                continue;
            }
            String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (normalized.isBlank() || name.contains(normalized)) {
                matches.add(material);
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(MaterialNames::display))
                .limit(Math.max(1, limit))
                .toList();
    }
}
