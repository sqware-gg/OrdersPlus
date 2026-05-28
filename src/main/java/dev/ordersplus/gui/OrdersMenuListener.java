package dev.ordersplus.gui;

import dev.ordersplus.order.OrderActionResult;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.MaterialNames;
import dev.ordersplus.util.Text;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class OrdersMenuListener implements Listener {
    private final OrderService service;
    private final OrdersGui gui;
    private final MaterialSearchPrompt materialSearchPrompt;

    public OrdersMenuListener(OrderService service, OrdersGui gui, MaterialSearchPrompt materialSearchPrompt) {
        this.service = service;
        this.gui = gui;
        this.materialSearchPrompt = materialSearchPrompt;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof OrdersMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.type()) {
            case BROWSE -> handleBrowse(player, holder, slot);
            case MANAGE -> handleManage(player, holder, slot, event.isRightClick());
            case CONFIRM_FULFILL -> handleConfirm(player, holder, slot);
            case MATERIAL_SEARCH -> handleMaterialSearch(player, holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OrdersMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBrowse(Player player, OrdersMenuHolder holder, int slot) {
        Long orderId = holder.orderAt(slot);
        if (orderId != null) {
            if (!player.hasPermission("ordersplus.fulfill")) {
                message(player, "no-permission", Map.of());
                return;
            }
            gui.openConfirm(player, orderId, holder.search(), holder.page());
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            gui.openBrowse(player, holder.search(), holder.page() - 1);
        } else if (slot == 47) {
            gui.openManage(player, 0);
        } else if (slot == 48) {
            materialSearchPrompt.open(player);
        } else if (slot == 49) {
            gui.openBrowse(player, holder.search(), holder.page());
        } else if (slot == 53) {
            gui.openBrowse(player, holder.search(), holder.page() + 1);
        }
    }

    private void handleManage(Player player, OrdersMenuHolder holder, int slot, boolean rightClick) {
        Long orderId = holder.orderAt(slot);
        if (orderId != null) {
            if (!rightClick) {
                send(player, service.claim(orderId, player, Integer.MAX_VALUE));
                gui.openManage(player, holder.page());
                return;
            }
            if (!player.hasPermission("ordersplus.cancel")) {
                message(player, "no-permission", Map.of());
                return;
            }
            send(player, service.cancel(orderId, player, false));
            gui.openManage(player, holder.page());
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            gui.openManage(player, holder.page() - 1);
        } else if (slot == 49) {
            gui.openManage(player, holder.page());
        } else if (slot == 53) {
            gui.openManage(player, holder.page() + 1);
        }
    }

    private void handleConfirm(Player player, OrdersMenuHolder holder, int slot) {
        if (slot == 11) {
            send(player, service.fulfill(holder.orderId(), player, Integer.MAX_VALUE));
            gui.openBrowse(player, holder.search(), holder.page());
        } else if (slot == 15) {
            gui.openBrowse(player, holder.search(), holder.page());
        }
    }

    private void handleMaterialSearch(Player player, OrdersMenuHolder holder, int slot) {
        Material material = holder.materialAt(slot);
        if (material != null) {
            message(player, "material-hint", Map.of("material", material.name().toLowerCase()));
            player.closeInventory();
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            gui.openMaterialSearch(player, holder.search(), holder.page() - 1);
        } else if (slot == 47) {
            materialSearchPrompt.open(player);
        } else if (slot == 49) {
            gui.openMaterialSearch(player, holder.search(), holder.page());
        } else if (slot == 53) {
            gui.openMaterialSearch(player, holder.search(), holder.page() + 1);
        }
    }

    private void send(Player player, OrderActionResult result) {
        message(player, result.messageKey(), result.placeholders());
    }

    private void message(Player player, String key, Map<String, String> placeholders) {
        String rendered = Text.color(service.config().prefix()
                + Text.render(service.config().message(key), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            player.sendMessage(line);
        }
    }
}
