package dev.ordersplus.gui;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class OrdersMenuHolder implements InventoryHolder {
    private final MenuType type;
    private final int page;
    private final String search;
    private final long orderId;
    private final Map<Integer, Long> orders = new HashMap<>();
    private final Map<Integer, Material> materials = new HashMap<>();
    private Inventory inventory;

    public OrdersMenuHolder(MenuType type, int page, String search, long orderId) {
        this.type = type;
        this.page = page;
        this.search = search;
        this.orderId = orderId;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public MenuType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public String search() {
        return search;
    }

    public long orderId() {
        return orderId;
    }

    public void mapOrder(int slot, long id) {
        orders.put(slot, id);
    }

    public Long orderAt(int slot) {
        return orders.get(slot);
    }

    public void mapMaterial(int slot, Material material) {
        materials.put(slot, material);
    }

    public Material materialAt(int slot) {
        return materials.get(slot);
    }
}
