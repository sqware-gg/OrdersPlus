package dev.ordersplus.order;

import dev.ordersplus.util.MaterialNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;

public final class Order {
    private final long id;
    private final UUID buyerUuid;
    private final String buyerName;
    private final Material material;
    private final int originalAmount;
    private int remainingAmount;
    private final double priceEach;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private OrderStatus status;
    private int fulfilledAmount;
    private int claimableAmount;

    public Order(long id, UUID buyerUuid, String buyerName, Material material, int originalAmount,
                 int remainingAmount, double priceEach, long createdAtMillis, long expiresAtMillis,
                 OrderStatus status, int fulfilledAmount, int claimableAmount) {
        this.id = id;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName == null ? "" : buyerName;
        this.material = material;
        this.originalAmount = Math.max(1, originalAmount);
        this.remainingAmount = Math.min(this.originalAmount, Math.max(0, remainingAmount));
        this.priceEach = Double.isFinite(priceEach) ? Math.max(0.0D, priceEach) : 0.0D;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.status = status == null ? OrderStatus.ACTIVE : status;
        int maxFulfilled = this.originalAmount - this.remainingAmount;
        this.fulfilledAmount = Math.min(maxFulfilled, Math.max(0, fulfilledAmount));
        this.claimableAmount = Math.min(this.fulfilledAmount, Math.max(0, claimableAmount));
    }

    public long id() {
        return id;
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

    public int originalAmount() {
        return originalAmount;
    }

    public int remainingAmount() {
        return remainingAmount;
    }

    public double priceEach() {
        return priceEach;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public OrderStatus status() {
        return status;
    }

    public int fulfilledAmount() {
        return fulfilledAmount;
    }

    public int claimableAmount() {
        return claimableAmount;
    }

    public boolean active() {
        return status == OrderStatus.ACTIVE;
    }

    public boolean expired(long nowMillis) {
        return active() && expiresAtMillis <= nowMillis;
    }

    public double remainingValue() {
        double value = remainingAmount * priceEach;
        return Double.isFinite(value) ? value : 0.0D;
    }

    public void fill(int amount) {
        int fillAmount = Math.min(Math.max(0, amount), remainingAmount);
        remainingAmount -= fillAmount;
        fulfilledAmount += fillAmount;
        claimableAmount += fillAmount;
        if (remainingAmount <= 0) {
            status = OrderStatus.COMPLETED;
        }
    }

    public void undoFill(int amount) {
        int rollbackAmount = Math.min(Math.max(0, amount), fulfilledAmount);
        fulfilledAmount -= rollbackAmount;
        claimableAmount = Math.max(0, claimableAmount - rollbackAmount);
        remainingAmount = Math.min(originalAmount, remainingAmount + rollbackAmount);
        if (remainingAmount > 0 && status == OrderStatus.COMPLETED) {
            status = OrderStatus.ACTIVE;
        }
    }

    public int claim(int amount) {
        int claimAmount = Math.min(Math.max(0, amount), claimableAmount);
        claimableAmount -= claimAmount;
        return claimAmount;
    }

    public void restoreClaimable(int amount) {
        claimableAmount = Math.min(fulfilledAmount, claimableAmount + Math.max(0, amount));
    }

    public void markCancelled() {
        status = OrderStatus.CANCELLED;
    }

    public void markExpired() {
        status = OrderStatus.EXPIRED;
    }

    public void markActive() {
        if (remainingAmount > 0) {
            status = OrderStatus.ACTIVE;
        }
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> haystack = new ArrayList<>();
        haystack.add(Long.toString(id));
        haystack.add(buyerName);
        haystack.add(material.name().replace('_', ' '));
        haystack.add(MaterialNames.display(material));
        return haystack.stream()
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalized));
    }
}
