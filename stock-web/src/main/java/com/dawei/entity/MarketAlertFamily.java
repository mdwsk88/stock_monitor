package com.dawei.entity;

import java.util.List;

/**
 * 宏观级/情绪级告警家族。
 */
public enum MarketAlertFamily {
    BULLISH,
    BEARISH,
    NEUTRAL;

    public static MarketAlertFamily fromMarketState(MarketState marketState) {
        if (marketState == null) {
            return NEUTRAL;
        }
        return switch (marketState) {
            case RISK_ON, OVERHEAT -> BULLISH;
            case DEFENSIVE -> BEARISH;
            default -> NEUTRAL;
        };
    }

    public static MarketAlertFamily fromPushType(AStockPushType pushType) {
        if (pushType == null) {
            return NEUTRAL;
        }
        return switch (pushType) {
            case MARKET_PULSE_OPPORTUNITY, MACRO_REALTIME_OPPORTUNITY -> BULLISH;
            case MARKET_PULSE_RISK, MACRO_REALTIME_RISK -> BEARISH;
            default -> NEUTRAL;
        };
    }

    public List<AStockPushType> pushTypes() {
        return switch (this) {
            case BULLISH -> List.of(
                    AStockPushType.MARKET_PULSE_OPPORTUNITY,
                    AStockPushType.MACRO_REALTIME_OPPORTUNITY
            );
            case BEARISH -> List.of(
                    AStockPushType.MARKET_PULSE_RISK,
                    AStockPushType.MACRO_REALTIME_RISK
            );
            case NEUTRAL -> List.of();
        };
    }

    public boolean conflictsWith(MarketAlertFamily other) {
        return this != NEUTRAL && other != NEUTRAL && this != other;
    }
}
