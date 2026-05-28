package dev.ordersplus.api.event;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class OrderFulfilledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final long orderId;
    private final UUID buyerUuid;
    private final String buyerName;
    private final UUID fulfillerUuid;
    private final String fulfillerName;
    private final Material material;
    private final String materialName;
    private final int amount;
    private final int remainingAmount;
    private final double payout;
    private final String formattedPayout;
    private final boolean completed;
    private final long fulfilledAtMillis;

    public OrderFulfilledEvent(long orderId, UUID buyerUuid, String buyerName, UUID fulfillerUuid,
                               String fulfillerName, Material material, String materialName, int amount,
                               int remainingAmount, double payout, String formattedPayout, boolean completed,
                               long fulfilledAtMillis) {
        this.orderId = orderId;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.fulfillerUuid = fulfillerUuid;
        this.fulfillerName = fulfillerName;
        this.material = material;
        this.materialName = materialName;
        this.amount = amount;
        this.remainingAmount = remainingAmount;
        this.payout = payout;
        this.formattedPayout = formattedPayout;
        this.completed = completed;
        this.fulfilledAtMillis = fulfilledAtMillis;
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

    public UUID fulfillerUuid() {
        return fulfillerUuid;
    }

    public String fulfillerName() {
        return fulfillerName;
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

    public int remainingAmount() {
        return remainingAmount;
    }

    public double payout() {
        return payout;
    }

    public String formattedPayout() {
        return formattedPayout;
    }

    public boolean completed() {
        return completed;
    }

    public long fulfilledAtMillis() {
        return fulfilledAtMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
