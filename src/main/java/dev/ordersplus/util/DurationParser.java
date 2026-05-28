package dev.ordersplus.util;

import java.util.Locale;
import java.util.OptionalLong;

public final class DurationParser {
    private DurationParser() {
    }

    public static OptionalLong parseMillis(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1000L;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (Character.isLetter(suffix)) {
            multiplier = switch (suffix) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> -1L;
            };
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (multiplier <= 0L || normalized.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(normalized);
            return value > 0L ? OptionalLong.of(Math.multiplyExact(value, multiplier)) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
