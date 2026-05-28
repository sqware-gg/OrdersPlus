package dev.ordersplus.util;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String compact(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m";
        }
        return Math.max(1L, seconds) + "s";
    }
}
