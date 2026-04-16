package com.example.ota.util;

import java.util.Comparator;

/**
 * Semantic version comparator.
 * Handles formats like "1.2.3", "1.0", "2" etc.
 * Falls back to string comparison for non-numeric segments.
 */
public class VersionComparator implements Comparator<String> {

    public static final VersionComparator INSTANCE = new VersionComparator();

    @Override
    public int compare(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLen = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLen; i++) {
            String s1 = i < parts1.length ? parts1[i] : "0";
            String s2 = i < parts2.length ? parts2[i] : "0";

            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2));
            } catch (NumberFormatException e) {
                cmp = s1.compareTo(s2);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
