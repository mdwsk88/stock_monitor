package com.dawei.entity;

import lombok.Getter;

/**
 * 市场快照链路健康状态。
 */
@Getter
public enum MarketSnapshotHealth {

    LIVE("在线", "Live"),
    DEGRADED("回退中", "Degraded"),
    DISCONNECTED("失联", "Disconnected");

    private final String label;
    private final String englishLabel;

    MarketSnapshotHealth(String label, String englishLabel) {
        this.label = label;
        this.englishLabel = englishLabel;
    }
}
