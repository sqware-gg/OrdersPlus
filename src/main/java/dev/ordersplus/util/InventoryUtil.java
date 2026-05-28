package dev.ordersplus.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static int count(Player player, Material material, boolean requirePlainItems) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (eligible(item, material, requirePlainItems)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static int totalCount(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static int spaceFor(Player player, Material material, boolean requirePlainItems) {
        int space = 0;
        int maxStackSize = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                space += maxStackSize;
            } else if (eligible(item, material, requirePlainItems)) {
                space += Math.max(0, maxStackSize - item.getAmount());
            }
        }
        return space;
    }

    public static boolean remove(Player player, Material material, int amount, boolean requirePlainItems) {
        if (amount <= 0 || count(player, material, requirePlainItems) < amount) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!eligible(item, material, requirePlainItems)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        inventory.setStorageContents(contents);
        return remaining == 0;
    }

    public static void returnItems(Player player, Material material, int amount) {
        int leftover = give(player, material, amount);
        if (leftover <= 0) {
            return;
        }
        while (leftover > 0) {
            int stackAmount = Math.min(leftover, material.getMaxStackSize());
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, stackAmount));
            leftover -= stackAmount;
        }
    }

    public static int give(Player player, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, material.getMaxStackSize());
            int leftoverAmount = 0;
            for (ItemStack leftover : player.getInventory().addItem(new ItemStack(material, stackAmount)).values()) {
                leftoverAmount += leftover.getAmount();
            }
            int accepted = stackAmount - leftoverAmount;
            if (accepted <= 0) {
                break;
            }
            remaining -= accepted;
        }
        return remaining;
    }

    private static boolean eligible(ItemStack item, Material material, boolean requirePlainItems) {
        if (item == null || item.getType() != material || item.getAmount() <= 0) {
            return false;
        }
        return !requirePlainItems || !item.hasItemMeta();
    }
}
