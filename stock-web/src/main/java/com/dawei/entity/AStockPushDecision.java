package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A股实时推送决策
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockPushDecision {

    private AStockPushType pushType;
    private String reason;
    private boolean critical;
    private boolean withinTradingSession;

    public boolean shouldSendRealtime() {
        return pushType == AStockPushType.REALTIME_OPPORTUNITY
                || pushType == AStockPushType.REALTIME_RISK;
    }
}
