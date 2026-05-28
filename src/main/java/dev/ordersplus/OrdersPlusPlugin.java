package dev.ordersplus;

import dev.ordersplus.api.OrdersPlusApi;
import dev.ordersplus.command.OrdersCommand;
import dev.ordersplus.command.OrdersPlusCommand;
import dev.ordersplus.config.ConfigReferenceWriter;
import dev.ordersplus.config.OrdersPlusConfig;
import dev.ordersplus.economy.EconomyService;
import dev.ordersplus.gui.MaterialSearchPrompt;
import dev.ordersplus.gui.OrdersGui;
import dev.ordersplus.gui.OrdersMenuListener;
import dev.ordersplus.order.OrderService;
import dev.ordersplus.order.OrderStore;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrdersPlusPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 31618;

    private OrdersPlusConfig ordersConfig;
    private OrderStore orderStore;
    private EconomyService economyService;
    private OrderService orderService;
    private OrdersGui ordersGui;
    private MaterialSearchPrompt materialSearchPrompt;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        ordersConfig = new OrdersPlusConfig(this);
        orderStore = new OrderStore(this);
        economyService = new EconomyService(this);
        orderService = new OrderService(this, ordersConfig, orderStore, economyService);
        ordersGui = new OrdersGui(orderService);
        materialSearchPrompt = new MaterialSearchPrompt(this, orderService, ordersGui);
        OrdersPlusApi.register(orderService);

        registerCommands();
        getServer().getPluginManager().registerEvents(new OrdersMenuListener(orderService, ordersGui, materialSearchPrompt), this);
        getServer().getPluginManager().registerEvents(materialSearchPrompt, this);
        orderService.start();

        if (!economyService.available()) {
            getLogger().warning("Vault is installed, but no economy provider is registered. Orders are disabled until one is available.");
        } else {
            getLogger().info("Hooked Vault economy provider: " + economyService.providerName());
        }
    }

    @Override
    public void onDisable() {
        OrdersPlusApi.unregister();
        if (orderService != null) {
            orderService.stop();
        }
    }

    private void registerCommands() {
        OrdersCommand ordersCommand = new OrdersCommand(orderService, ordersGui, materialSearchPrompt);
        PluginCommand orders = getCommand("orders");
        if (orders != null) {
            orders.setExecutor(ordersCommand);
            orders.setTabCompleter(ordersCommand);
        }

        OrdersPlusCommand adminCommand = new OrdersPlusCommand(orderService);
        PluginCommand admin = getCommand("ordersplus");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }
}
