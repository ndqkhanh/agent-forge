package com.agentforge.common.util;

/**
 * Lightweight validation utilities.
 */
public final class Preconditions {

    private Preconditions() {}

    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
        return value;
    }

    public static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
        return value;
    }

    public static double requireInRange(double value, double min, double max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                name + " must be in [" + min + ", " + max + "], got: " + value
            );
        }
        return value;
    }

    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
