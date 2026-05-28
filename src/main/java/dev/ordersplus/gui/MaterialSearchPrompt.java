package dev.ordersplus.gui;

import dev.ordersplus.order.OrderService;
import dev.ordersplus.util.Text;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class MaterialSearchPrompt implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int MAX_QUERY_LENGTH = 48;
    private static final long SESSION_MILLIS = 30_000L;
    private static final long SESSION_TICKS = 20L * 30L;
    private static final double MAX_INPUT_DISTANCE_SQUARED = 12.0D * 12.0D;

    private final JavaPlugin plugin;
    private final OrderService service;
    private final OrdersGui gui;
    private final Map<UUID, PromptSession> pendingSearches = new ConcurrentHashMap<>();

    public MaterialSearchPrompt(JavaPlugin plugin, OrderService service, OrdersGui gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    public void open(Player player) {
        PromptSession previous = pendingSearches.remove(player.getUniqueId());
        if (previous != null) {
            restore(player, previous);
        }

        Location location = promptLocation(player);
        BlockState originalState = location.getBlock().getState();
        PromptSession session = new PromptSession(
                Position.block(location).toBlock(),
                location,
                player.getWorld().getUID(),
                location.getBlock().getBlockData(),
                originalState,
                System.currentTimeMillis() + SESSION_MILLIS
        );
        pendingSearches.put(player.getUniqueId(), session);

        player.closeInventory();
        message(player, "search-sign-opened", Map.of());
        Bukkit.getScheduler().runTask(plugin, () -> showAndOpen(player, session));
        Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(player, session), SESSION_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignInput(UncheckedSignChangeEvent event) {
        PromptSession session = pendingSearches.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (!samePosition(session.position(), event.getEditedBlockPosition())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!validSession(player, session) || event.getSide() != Side.FRONT) {
            return;
        }

        pendingSearches.remove(player.getUniqueId());
        String query = sanitizeQuery(event.lines().isEmpty() ? "" : PLAIN.serialize(event.lines().getFirst()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            restore(player, session);
            if (query.isBlank()) {
                message(player, "search-empty", Map.of());
                return;
            }
            gui.openMaterialSearch(player, query, 0);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingSearches.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        PromptSession session = pendingSearches.remove(event.getPlayer().getUniqueId());
        if (session != null && event.getPlayer().isOnline()) {
            restore(event.getPlayer(), session);
        }
    }

    private void showAndOpen(Player player, PromptSession session) {
        if (!player.isOnline() || !pending(player, session)) {
            return;
        }
        BlockData signData = Material.OAK_SIGN.createBlockData();
        player.sendBlockChange(session.location(), signData);
        sendSignState(player, session.location(), signData);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && pending(player, session)) {
                player.openVirtualSign(session.position(), Side.FRONT);
            }
        }, 2L);
    }

    private void sendSignState(Player player, Location location, BlockData signData) {
        BlockState state = signData.createBlockState();
        if (!(state instanceof Sign sign)) {
            return;
        }
        sign.setWaxed(false);
        sign.setAllowedEditorUniqueId(player.getUniqueId());
        sign.getSide(Side.FRONT).line(0, Component.empty());
        sign.getSide(Side.FRONT).line(1, Component.text("^ ^ ^"));
        sign.getSide(Side.FRONT).line(2, Component.text("Type above"));
        sign.getSide(Side.FRONT).line(3, Component.empty());
        player.sendBlockUpdate(location, sign);
    }

    private Location promptLocation(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        Vector behind = player.getLocation().getDirection().setY(0.0D);
        if (behind.lengthSquared() < 0.01D) {
            behind = new Vector(0.0D, 0.0D, 1.0D);
        } else {
            behind.normalize();
        }
        Vector side = new Vector(-behind.getZ(), 0.0D, behind.getX());
        Vector[] offsets = new Vector[]{
                new Vector(0.0D, 2.0D, 0.0D),
                behind.clone().multiply(-2.0D).add(new Vector(0.0D, 1.0D, 0.0D)),
                behind.clone().multiply(-2.0D).add(new Vector(0.0D, 2.0D, 0.0D)),
                side.clone().multiply(2.0D).add(new Vector(0.0D, 1.0D, 0.0D)),
                side.clone().multiply(-2.0D).add(new Vector(0.0D, 1.0D, 0.0D)),
                new Vector(0.0D, 3.0D, 0.0D)
        };
        for (Vector offset : offsets) {
            Location candidate = base.clone().add(offset);
            if (safeVirtualSignLocation(candidate)) {
                return candidate;
            }
        }
        return base.add(0.0D, 2.0D, 0.0D);
    }

    private boolean safeVirtualSignLocation(Location location) {
        int y = location.getBlockY();
        if (y < location.getWorld().getMinHeight() || y >= location.getWorld().getMaxHeight()) {
            return false;
        }
        return location.getBlock().getType().isAir();
    }

    private void timeout(Player player, PromptSession session) {
        PromptSession current = pendingSearches.get(player.getUniqueId());
        if (current == null || !samePosition(current.position(), session.position())) {
            return;
        }
        pendingSearches.remove(player.getUniqueId());
        if (player.isOnline()) {
            restore(player, session);
        }
    }

    private boolean pending(Player player, PromptSession session) {
        PromptSession current = pendingSearches.get(player.getUniqueId());
        return current != null
                && samePosition(current.position(), session.position())
                && current.worldId().equals(session.worldId());
    }

    private void restore(Player player, PromptSession session) {
        if (!player.getWorld().getUID().equals(session.worldId())) {
            return;
        }
        player.sendBlockChange(session.location(), session.originalBlockData());
        if (session.originalState() instanceof TileState tileState) {
            player.sendBlockUpdate(session.location(), tileState);
        }
    }

    private boolean validSession(Player player, PromptSession session) {
        if (!player.getWorld().getUID().equals(session.worldId())) {
            pendingSearches.remove(player.getUniqueId());
            return false;
        }
        if (System.currentTimeMillis() > session.expiresAtMillis()) {
            pendingSearches.remove(player.getUniqueId());
            restore(player, session);
            return false;
        }
        if (player.getLocation().distanceSquared(session.location()) > MAX_INPUT_DISTANCE_SQUARED) {
            pendingSearches.remove(player.getUniqueId());
            restore(player, session);
            return false;
        }
        return true;
    }

    private String sanitizeQuery(String raw) {
        String sanitized = raw == null ? "" : raw.replace('\u00A7', ' ');
        sanitized = sanitized.replaceAll("\\p{Cntrl}", " ");
        sanitized = sanitized.replaceAll("[^\\p{L}\\p{N}_\\- ]", " ");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.length() > MAX_QUERY_LENGTH) {
            sanitized = sanitized.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return sanitized;
    }

    private boolean samePosition(BlockPosition first, BlockPosition second) {
        return first.blockX() == second.blockX()
                && first.blockY() == second.blockY()
                && first.blockZ() == second.blockZ();
    }

    private void message(Player player, String key, Map<String, String> placeholders) {
        String rendered = Text.color(service.config().prefix()
                + Text.render(service.config().message(key), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            player.sendMessage(line);
        }
    }

    private record PromptSession(BlockPosition position, Location location, UUID worldId, BlockData originalBlockData,
                                 BlockState originalState, long expiresAtMillis) {
    }
}
