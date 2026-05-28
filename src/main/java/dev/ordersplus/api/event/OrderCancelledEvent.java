package dev.ordersplus.api.event;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class OrderCancelledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final long orderId;
    private final UUID buyerUuid;
    private final String buyerName;
    private final String cancelledByName;
    private final Material material;
    private final String materialName;
    private final int remainingAmount;
    private final double refund;
    private final String formattedRefund;
    private final long cancelledAtMillis;

    public OrderCancelledEvent(long orderId, UUID buyerUuid, String buyerName, String cancelledByName,
                               Material material, String materialName, int remainingAmount, double refund,
                               String formattedRefund, long cancelledAtMillis) {
        this.orderId = orderId;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.cancelledByName = cancelledByName;
        this.material = material;
        this.materialName = materialName;
        this.remainingAmount = remainingAmount;
        this.refund = refund;
        this.formattedRefund = formattedRefund;
        this.cancelledAtMillis = cancelledAtMillis;
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

    public String cancelledByName() {
        return cancelledByName;
    }

    public Material material() {
        return material;
    }

    public String materialName() {
        return materialName;
    }

    public int remainingAmount() {
        return remainingAmount;
    }

    public double refund() {
        return refund;
    }

    public String formattedRefund() {
        return formattedRefund;
    }

    public long cancelledAtMillis() {
        return cancelledAtMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
