package test.java.com.warnomodmaker;

import java.util.*;

/**
 * Enhanced assertion framework for testing
 */
public class TestAssert {
    private static int assertionCount = 0;
    private static int passedAssertions = 0;

    public static void assertTrue(String message, boolean condition) {
        assertionCount++;
        if (!condition) {
            throw new AssertionError("ASSERTION FAILED: " + message);
        }
        passedAssertions++;
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    public static void assertNotNull(String message, Object obj) {
        assertTrue(message + " (was null)", obj != null);
    }

    public static void assertNull(String message, Object obj) {
        assertTrue(message + " (was not null: " + obj + ")", obj == null);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        assertionCount++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Expected: " + formatValue(expected) +
                "\n  Actual:   " + formatValue(actual));
        }
        passedAssertions++;
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        assertionCount++;
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Expected: " + expected + " (±" + delta + ")" +
                "\n  Actual:   " + actual +
                "\n  Difference: " + Math.abs(expected - actual));
        }
        passedAssertions++;
    }

    public static void assertGreaterThan(String message, double actual, double threshold) {
        assertionCount++;
        if (actual <= threshold) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Expected: > " + threshold +
                "\n  Actual:   " + actual);
        }
        passedAssertions++;
    }

    public static void assertLessThan(String message, double actual, double threshold) {
        assertionCount++;
        if (actual >= threshold) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Expected: < " + threshold +
                "\n  Actual:   " + actual);
        }
        passedAssertions++;
    }

    public static void assertContains(String message, String text, String substring) {
        assertionCount++;
        if (text == null || !text.contains(substring)) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Text: " + formatValue(text) +
                "\n  Should contain: " + formatValue(substring));
        }
        passedAssertions++;
    }

    public static void assertNotEmpty(String message, Collection<?> collection) {
        assertionCount++;
        if (collection == null || collection.isEmpty()) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Collection was " + (collection == null ? "null" : "empty"));
        }
        passedAssertions++;
    }

    public static void assertSize(String message, Collection<?> collection, int expectedSize) {
        assertionCount++;
        if (collection == null) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Collection was null, expected size: " + expectedSize);
        }
        if (collection.size() != expectedSize) {
            throw new AssertionError("ASSERTION FAILED: " + message +
                "\n  Expected size: " + expectedSize +
                "\n  Actual size:   " + collection.size());
        }
        passedAssertions++;
    }

    public static void fail(String message) {
        assertionCount++;
        throw new AssertionError("TEST FAILED: " + message);
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        return value.toString();
    }

    public static int getAssertionCount() { return assertionCount; }
    public static int getPassedAssertions() { return passedAssertions; }
    public static void resetCounters() { assertionCount = 0; passedAssertions = 0; }
}
