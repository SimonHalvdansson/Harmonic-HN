package com.simon.harmonichackernews.utils;

public final class AccessibilityTextUtils {

    private AccessibilityTextUtils() {
    }

    public static String commentCountDescription(int count) {
        if (count == 1) {
            return "1 comment";
        }
        return count + " comments";
    }

    public static String pointCountDescription(int count) {
        if (count == 1) {
            return "1 point";
        }
        return count + " points";
    }
}
