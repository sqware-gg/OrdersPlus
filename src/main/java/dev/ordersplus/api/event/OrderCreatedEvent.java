package dev.ordersplus.api.event;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class OrderCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final long orderId;
    private final UUID buyerUuid;
    private final String buyerName;
    private final Material material;
    private final String materialName;
    private final int amount;
    private final double priceEach;
    private final String formattedPriceEach;
    private final double total;
    private final String formattedTotal;
    private final long createdAtMillis;
    private final long expiresAtMillis;

    public OrderCreatedEvent(long orderId, UUID buyerUuid, String buyerName, Material material, String materialName,
                             int amount, double priceEach, String formattedPriceEach, double total,
                             String formattedTotal, long createdAtMillis, long expiresAtMillis) {
        this.orderId = orderId;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.material = material;
        this.materialName = materialName;
        this.amount = amount;
        this.priceEach = priceEach;
        this.formattedPriceEach = formattedPriceEach;
        this.total = total;
        this.formattedTotal = formattedTotal;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public long orderId() {
        return orderId;
    }

    public UUID buyerUuid() {
        return buyerUuid;
    }

    public String buyerName() {
        return buyerName;
    }

    public Material material() {
        return material;
    }

    public String materialName() {
        return materialName;
    }

    public int amount() {
        return amount;
    }

    public double priceEach() {
        return priceEach;
    }

    public String formattedPriceEach() {
        return formattedPriceEach;
    }

    public double total() {
        return total;
    }

    public String formattedTotal() {
        return formattedTotal;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
