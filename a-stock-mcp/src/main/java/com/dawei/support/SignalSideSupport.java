package com.dawei.support;

import org.springframework.util.StringUtils;

/**
 * 统一 MCP 内部的多空方向值域。
 * 主项目 stock-web 使用“利多/利空/中性”，MCP 对外保持 BUY/SELL/NEUTRAL。
 */
public final class SignalSideSupport {

    public static final String BUY = "BUY";
    public static final String SELL = "SELL";
    public static final String NEUTRAL = "NEUTRAL";

    private SignalSideSupport() {
    }

    public static String normalize(String rawSide) {
        if (!StringUtils.hasText(rawSide)) {
            return NEUTRAL;
        }
        String normalized = rawSide.trim();
        return switch (normalized) {
            case "BUY", "buy", "Buy", "利多", "看多", "bullish", "BULLISH" -> BUY;
            case "SELL", "sell", "Sell", "利空", "看空", "bearish", "BEARISH" -> SELL;
            default -> NEUTRAL;
        };
    }

    public static String toLabel(String side) {
        return switch (normalize(side)) {
            case BUY -> "利多";
            case SELL -> "利空";
            default -> "中性";
        };
    }

    public static boolean isCompatible(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (NEUTRAL.equals(normalizedLeft) || NEUTRAL.equals(normalizedRight)) {
            return true;
        }
        return normalizedLeft.equals(normalizedRight);
    }
}
