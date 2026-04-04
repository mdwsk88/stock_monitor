package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 盘中市场快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshot {

    private MarketState marketState = MarketState.NEUTRAL;
    private MarketState rawMarketState = MarketState.NEUTRAL;
    private MarketSnapshotHealth snapshotHealth = MarketSnapshotHealth.LIVE;
    private LocalDateTime capturedAt;
    private LocalDateTime stateConfirmedAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private LocalDateTime nextRetryAt;
    private double shChangePct;
    private double szChangePct;
    private double cybChangePct;
    private int upCount;
    private int downCount;
    private int flatCount;
    private int limitUpCount;
    private int limitDownCount;
    private int breadthSampleSize;
    private String source;
    private int consecutiveFailureCount;
    private boolean fallback;
    private boolean stale;
    private String lastFailureReason;

    public static MarketSnapshot neutral(LocalDateTime capturedAt, String source) {
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setMarketState(MarketState.NEUTRAL);
        snapshot.setRawMarketState(MarketState.NEUTRAL);
        snapshot.setSnapshotHealth(MarketSnapshotHealth.LIVE);
        snapshot.setCapturedAt(capturedAt);
        snapshot.setStateConfirmedAt(capturedAt);
        snapshot.setLastSuccessAt(capturedAt);
        snapshot.setSource(source);
        return snapshot;
    }

    public boolean hasBreadthData() {
        return upCount > 0 || downCount > 0 || flatCount > 0;
    }

    public int getBreadthTotal() {
        return Math.max(0, upCount) + Math.max(0, downCount) + Math.max(0, flatCount);
    }

    public double getAverageIndexChangePct() {
        return (shChangePct + szChangePct + cybChangePct) / 3.0;
    }

    public double getStrongestIndexChangePct() {
        return Math.max(shChangePct, Math.max(szChangePct, cybChangePct));
    }

    public double getWeakestIndexChangePct() {
        return Math.min(shChangePct, Math.min(szChangePct, cybChangePct));
    }

    public double getAdvanceDeclineRatio() {
        int total = upCount + downCount;
        if (total <= 0) {
            return 0.5d;
        }
        return upCount * 1.0d / total;
    }
}
