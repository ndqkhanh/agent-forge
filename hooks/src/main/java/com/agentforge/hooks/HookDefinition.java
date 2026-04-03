package com.agentforge.hooks;

import java.util.List;

public record HookDefinition(
        String name,
        HookType type,
        String command,
        List<String> toolPatterns) {

    public HookDefinition {
        toolPatterns = List.copyOf(toolPatterns);
    }

    /**
     * Returns true if the given toolName matches any of this hook's toolPatterns.
     * Supports a single {@code *} wildcard that matches any sequence of characters.
     */
    public boolean matchesTool(String toolName) {
        if (toolName == null) return false;
        for (String pattern : toolPatterns) {
            if (matchesPattern(pattern, toolName)) return true;
        }
        return false;
    }

    private static boolean matchesPattern(String pattern, String toolName) {
        if (pattern.equals("*")) return true;
        if (!pattern.contains("*")) return pattern.equals(toolName);

        // Split on * and match prefix/suffix
        int starIdx = pattern.indexOf('*');
        String prefix = pattern.substring(0, starIdx);
        String suffix = pattern.substring(starIdx + 1);

        // Only single * supported; if there's another * in suffix, fall back to regex-style
        if (suffix.contains("*")) {
            return matchesMultiWildcard(pattern, toolName);
        }

        return toolName.startsWith(prefix) && toolName.endsWith(suffix)
                && toolName.length() >= prefix.length() + suffix.length();
    }

    private static boolean matchesMultiWildcard(String pattern, String toolName) {
        // Convert glob pattern to a simple recursive match
        return globMatch(pattern, 0, toolName, 0);
    }

    private static boolean globMatch(String pattern, int pi, String text, int ti) {
        while (pi < pattern.length()) {
            char pc = pattern.charAt(pi);
            if (pc == '*') {
                // Skip consecutive stars
                while (pi < pattern.length() && pattern.charAt(pi) == '*') pi++;
                if (pi == pattern.length()) return true;
                // Try matching the rest at every position in text
                for (int i = ti; i <= text.length(); i++) {
                    if (globMatch(pattern, pi, text, i)) return true;
                }
                return false;
            } else {
                if (ti >= text.length() || pc != text.charAt(ti)) return false;
                pi++;
                ti++;
            }
        }
        return ti == text.length();
    }
}
