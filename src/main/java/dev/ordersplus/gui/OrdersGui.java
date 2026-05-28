package dev.ordersplus.gui;

import dev.ordersplus.order.Order;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.DurationFormatter;
import dev.ordersplus.util.InventoryUtil;
import dev.ordersplus.util.MaterialNames;
import dev.ordersplus.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class OrdersGui {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;

    private final OrderService service;

    public OrdersGui(OrderService service) {
        this.service = service;
    }

    public void openBrowse(Player player, String search, int requestedPage) {
        List<Order> orders = service.browseOrders(search);
        int pages = Math.max(1, (int) Math.ceil(orders.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        OrdersMenuHolder holder = new OrdersMenuHolder(MenuType.BROWSE, page, search, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                Text.component(service.config().guiTitle("title-browse")));
        holder.attach(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, orders.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Order order = orders.get(index);
            inventory.setItem(slot, orderItem(order, List.of("&#57F287Click to fulfil.")));
            holder.mapOrder(slot, order.id());
        }
        if (orders.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "&#ED4245No orders", List.of("&7Try another search.")));
        }

        inventory.setItem(45, icon(Material.ARROW, "&#2b98fdPrevious Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        inventory.setItem(47, icon(Material.CHEST, "&#2b98fdYour Orders", List.of("&7View and cancel your active orders.")));
        inventory.setItem(48, icon(Material.OAK_SIGN, "&#2b98fdItem Search", List.of("&7Type an item name on a sign.")));
        inventory.setItem(49, icon(Material.SUNFLOWER, "&#2b98fdRefresh", List.of("&7Reload this page.")));
        String searchText = search == null || search.isBlank() ? "None" : search;
        inventory.setItem(50, icon(Material.NAME_TAG, "&7Search: &#2b98fd" + searchText, List.of("&7Use &f/orders search <text>&7.")));
        inventory.setItem(53, icon(Material.ARROW, "&#2b98fdNext Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        player.openInventory(inventory);
    }

    public void openManage(Player player, int requestedPage) {
        List<Order> orders = service.manageableOrders(player.getUniqueId());
        int pages = Math.max(1, (int) Math.ceil(orders.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        OrdersMenuHolder holder = new OrdersMenuHolder(MenuType.MANAGE, page, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                Text.component(service.config().guiTitle("title-manage")));
        holder.attach(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, orders.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Order order = orders.get(index);
            inventory.setItem(slot, orderItem(order, List.of(
                    "&#57F287Left-click to claim fulfilled items.",
                    "&#ED4245Right-click to cancel and refund remaining escrow."
            )));
            holder.mapOrder(slot, order.id());
        }
        if (orders.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "&#ED4245No active orders", List.of("&7Create one with &f/orders create&7.")));
        }
        inventory.setItem(45, icon(Material.ARROW, "&#2b98fdPrevious Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        inventory.setItem(49, icon(Material.SUNFLOWER, "&#2b98fdRefresh", List.of("&7Reload your orders.")));
        inventory.setItem(53, icon(Material.ARROW, "&#2b98fdNext Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, long orderId, String search, int page) {
        OrdersMenuHolder holder = new OrdersMenuHolder(MenuType.CONFIRM_FULFILL, page, search, orderId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Text.component(service.config().guiTitle("title-confirm")));
        holder.attach(inventory);
        service.browseOrders(search).stream()
                .filter(order -> order.id() == orderId)
                .findFirst()
                .ifPresent(order -> {
                    int available = InventoryUtil.count(player, order.material(), service.config().requirePlainItems());
                    int fillAmount = Math.min(available, order.remainingAmount());
                    inventory.setItem(13, orderItem(order, List.of()));
                    if (fillAmount > 0) {
                        inventory.setItem(11, icon(Material.LIME_CONCRETE, "&#57F287Fulfil " + fillAmount,
                                List.of("&7Payout: &#2b98fd" + service.formatMoney(fillAmount * order.priceEach()))));
                    } else {
                        inventory.setItem(11, icon(Material.BARRIER, "&#ED4245No matching items",
                                List.of("&7You need &f" + MaterialNames.display(order.material()) + "&7.")));
                    }
                });
        inventory.setItem(15, icon(Material.RED_CONCRETE, "&#ED4245Cancel", List.of("&7Return to orders.")));
        player.openInventory(inventory);
    }

    public void openMaterialSearch(Player player, String search, int requestedPage) {
        String query = search == null ? "" : search.trim();
        List<Material> materials = query.isBlank()
                ? List.of()
                : MaterialNames.searchItems(query, 500).stream()
                        .filter(material -> service.config().allowNonStackableItems() || material.getMaxStackSize() > 1)
                        .toList();
        int pages = Math.max(1, (int) Math.ceil(materials.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        OrdersMenuHolder holder = new OrdersMenuHolder(MenuType.MATERIAL_SEARCH, page, search, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                Text.component(service.config().guiTitle("title-materials")));
        holder.attach(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, materials.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Material material = materials.get(index);
            inventory.setItem(slot, icon(material, "&#2b98fd" + MaterialNames.display(material),
                    List.of("&7ID: &f" + material.name().toLowerCase(), "&#57F287Click for create command hint.")));
            holder.mapMaterial(slot, material);
        }
        if (materials.isEmpty()) {
            Material icon = query.isBlank() ? Material.OAK_SIGN : Material.BARRIER;
            String name = query.isBlank() ? "&#2b98fdSearch Items" : "&#ED4245No Items Found";
            List<String> lore = query.isBlank()
                    ? List.of("&7Click the sign below and type an item name.")
                    : List.of("&7Search: &f" + query, "&7Try a shorter item name.");
            inventory.setItem(22, icon(icon, name, lore));
        }
        inventory.setItem(45, icon(Material.ARROW, "&#2b98fdPrevious Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        inventory.setItem(47, icon(Material.OAK_SIGN, "&#2b98fdNew Search", List.of("&7Type a new item name on a sign.")));
        inventory.setItem(49, icon(Material.SUNFLOWER, "&#2b98fdRefresh", List.of("&7Reload this search.")));
        String searchText = query.isBlank() ? "None" : query;
        inventory.setItem(50, icon(Material.NAME_TAG, "&7Search: &#2b98fd" + searchText, List.of("&7Use &f/orders items&7 to search again.")));
        inventory.setItem(53, icon(Material.ARROW, "&#2b98fdNext Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        player.openInventory(inventory);
    }

    private ItemStack orderItem(Order order, List<String> extraLore) {
        ItemStack item = new ItemStack(order.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.component("&#2b98fdOrder #" + order.id() + " &8- &f" + MaterialNames.display(order.material())));
        List<String> lore = new ArrayList<>();
        lore.add("&7Buyer: &f" + order.buyerName());
        lore.add("&7Remaining: &#2b98fd" + order.remainingAmount() + "&8/&f" + order.originalAmount());
        lore.add("&7Ready to Claim: &#57F287" + order.claimableAmount());
        lore.add("&7Status: &f" + order.status().name().toLowerCase().replace('_', ' '));
        lore.add("&7Price Each: &#2b98fd" + service.formatMoney(order.priceEach()));
        lore.add("&7Remaining Value: &#2b98fd" + service.formatMoney(order.remainingValue()));
        lore.add("&7Expires: &f" + DurationFormatter.compact(order.expiresAtMillis() - System.currentTimeMillis()));
        lore.addAll(extraLore);
        meta.lore(components(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(name));
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> components(List<String> lore) {
        return lore.stream().map(Text::component).toList();
    }
}
