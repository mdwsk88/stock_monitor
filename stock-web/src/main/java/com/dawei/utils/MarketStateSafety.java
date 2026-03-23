package com.dawei.utils;

import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import org.apache.commons.lang3.StringUtils;

/**
 * 市场状态安全阀：快照退化时，不继续沿用激进的风险偏好状态。
 */
public final class MarketStateSafety {

    private MarketStateSafety() {
    }

    public static MarketState normalize(MarketSnapshot snapshot, int sampleWarnThreshold) {
        if (snapshot == null || snapshot.getMarketState() == null) {
            return MarketState.NEUTRAL;
        }

        MarketState marketState = snapshot.getMarketState();
        if (snapshot.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED) {
            return MarketState.DEFENSIVE;
        }

        if (snapshot.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED || hasNoBreadth(snapshot)) {
            return downgradeAggressiveState(marketState);
        }

        if (hasLowConfidenceSampleBreadth(snapshot, sampleWarnThreshold) && marketState == MarketState.OVERHEAT) {
            return MarketState.RISK_ON;
        }

        return marketState;
    }

    public static boolean hasNoBreadth(MarketSnapshot snapshot) {
        return snapshot != null
                && StringUtils.containsIgnoreCase(StringUtils.defaultString(snapshot.getSource()), "NO_BREADTH");
    }

    public static boolean hasLowConfidenceSampleBreadth(MarketSnapshot snapshot, int sampleWarnThreshold) {
        return snapshot != null
                && StringUtils.containsIgnoreCase(StringUtils.defaultString(snapshot.getSource()), "SAMPLE_BREADTH")
                && snapshot.getBreadthSampleSize() > 0
                && snapshot.getBreadthSampleSize() < Math.max(1, sampleWarnThreshold);
    }

    private static MarketState downgradeAggressiveState(MarketState marketState) {
        if (marketState == MarketState.RISK_ON || marketState == MarketState.OVERHEAT) {
            return MarketState.NEUTRAL;
        }
        return marketState != null ? marketState : MarketState.NEUTRAL;
    }
}
