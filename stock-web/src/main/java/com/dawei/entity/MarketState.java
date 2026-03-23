package com.dawei.entity;

/**
 * A股市场状态机。
 */
public enum MarketState {
    DEFENSIVE("防守态"),
    NEUTRAL("中性"),
    RISK_ON("进攻态"),
    OVERHEAT("高潮态");

    private final String label;

    MarketState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRiskOff() {
        return this == DEFENSIVE;
    }

    public boolean isRiskOn() {
        return this == RISK_ON || this == OVERHEAT;
    }
}
