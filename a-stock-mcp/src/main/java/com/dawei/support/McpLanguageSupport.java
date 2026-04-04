package com.dawei.support;

import org.springframework.util.StringUtils;

public final class McpLanguageSupport {

    public static final String ZH = "zh";
    public static final String EN = "en";

    private McpLanguageSupport() {
    }

    public static boolean isEnglish(String language) {
        return EN.equalsIgnoreCase(normalize(language));
    }

    public static String normalize(String language) {
        if (!StringUtils.hasText(language)) {
            return ZH;
        }
        String normalized = language.trim().toLowerCase();
        return EN.equals(normalized) ? EN : ZH;
    }
}
