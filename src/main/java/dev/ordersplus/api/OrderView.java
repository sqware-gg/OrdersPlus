package dev.ordersplus.api;

public record OrderView(long id, String buyerName, String materialName, String materialKey,
                        int originalAmount, int remainingAmount, int fulfilledAmount, int claimableAmount,
                        double priceEach, String formattedPriceEach, double remainingValue,
                        String formattedRemainingValue, String status, long createdAtMillis,
                        long expiresAtMillis) {
}
