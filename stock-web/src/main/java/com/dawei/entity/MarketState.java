package com.dawei.entity;

/**
 * A股市场状态机。
 */
public enum MarketState {
    DEFENSIVE("防守态", "Defensive"),
    NEUTRAL("中性", "Neutral"),
    RISK_ON("进攻态", "Risk-On"),
    OVERHEAT("高潮态", "Overheated");

    private final String label;
    private final String englishLabel;

    MarketState(String label, String englishLabel) {
        this.label = label;
        this.englishLabel = englishLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getEnglishLabel() {
        return englishLabel;
    }

    public boolean isRiskOff() {
        return this == DEFENSIVE;
    }

    public boolean isRiskOn() {
        return this == RISK_ON || this == OVERHEAT;
    }
}
