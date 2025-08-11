package com.warnomodmaker.util;

/**
 * Utility class for string similarity calculations
 */
public class StringSimilarity {

    /**
     * Calculate Levenshtein distance between two strings
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1.length() == 0) return s2.length();
        if (s2.length() == 0) return s1.length();

        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculate string similarity using Levenshtein distance (0.0 to 1.0)
     */
    public static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * Calculate token-based similarity for multi-word strings
     */
    public static double calculateTokenSimilarity(String target, String candidate) {
        String[] targetTokens = target.split("[\\s_-]+");
        String[] candidateTokens = candidate.split("[\\s_-]+");

        int matches = 0;
        for (String targetToken : targetTokens) {
            for (String candidateToken : candidateTokens) {
                if (targetToken.equals(candidateToken) ||
                    targetToken.contains(candidateToken) ||
                    candidateToken.contains(targetToken)) {
                    matches++;
                    break;
                }
            }
        }

        return (double) matches / Math.max(targetTokens.length, candidateTokens.length);
    }

    /**
     * Calculate similarity based on common prefix and suffix
     */
    public static double calculateAffixSimilarity(String target, String candidate) {
        int commonPrefix = 0;
        int minLength = Math.min(target.length(), candidate.length());

        for (int i = 0; i < minLength; i++) {
            if (target.charAt(i) == candidate.charAt(i)) {
                commonPrefix++;
            } else {
                break;
            }
        }

        int commonSuffix = 0;
        for (int i = 1; i <= minLength - commonPrefix; i++) {
            if (target.charAt(target.length() - i) == candidate.charAt(candidate.length() - i)) {
                commonSuffix++;
            } else {
                break;
            }
        }

        return (double) (commonPrefix + commonSuffix) / Math.max(target.length(), candidate.length());
    }
}
