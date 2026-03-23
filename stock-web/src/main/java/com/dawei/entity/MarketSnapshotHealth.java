package com.dawei.entity;

import lombok.Getter;

/**
 * 市场快照链路健康状态。
 */
@Getter
public enum MarketSnapshotHealth {

    LIVE("在线"),
    DEGRADED("回退中"),
    DISCONNECTED("失联");

    private final String label;

    MarketSnapshotHealth(String label) {
        this.label = label;
    }
}
