package com.dawei.utils;

import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketStateSafetyTest {

    @Test
    void normalize_ShouldFallbackToDefensiveWhenSnapshotDisconnected() {
        assertEquals(MarketState.DEFENSIVE, MarketStateSafety.normalize(
                snapshot(MarketState.RISK_ON, MarketSnapshotHealth.DISCONNECTED, "TENCENT_QUOTE+NO_BREADTH", 0),
                200
        ));
    }

    @Test
    void normalize_ShouldFallbackToNeutralWhenBreadthUnavailable() {
        assertEquals(MarketState.NEUTRAL, MarketStateSafety.normalize(
                snapshot(MarketState.RISK_ON, MarketSnapshotHealth.LIVE, "TENCENT_QUOTE+NO_BREADTH", 0),
                200
        ));
    }

    @Test
    void normalize_ShouldDowngradeOverheatWhenSampleBreadthWeak() {
        assertEquals(MarketState.RISK_ON, MarketStateSafety.normalize(
                snapshot(MarketState.OVERHEAT, MarketSnapshotHealth.LIVE, "EASTMONEY+SAMPLE_BREADTH", 80),
                200
        ));
    }

    private MarketSnapshot snapshot(MarketState marketState,
                                    MarketSnapshotHealth health,
                                    String source,
                                    int breadthSampleSize) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 23, 10, 0), source);
        snapshot.setMarketState(marketState);
        snapshot.setSnapshotHealth(health);
        snapshot.setBreadthSampleSize(breadthSampleSize);
        return snapshot;
    }
}
