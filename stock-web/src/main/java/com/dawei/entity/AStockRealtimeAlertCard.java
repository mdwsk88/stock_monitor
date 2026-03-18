package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A股盘中实时预警卡片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockRealtimeAlertCard {

    private String stockCode;
    private String stockName;
    private AStockPushType pushType;
    private String severityLabel;
    private String signalSide;
    private Integer signalScore;
    private String eventType;
    private String title;
    private String conclusion;
    private String reasoning;
    private String riskHint;
    private String macroThemeName;
    private String macroTitle;
    private Integer macroSignalScore;
    private Integer resonanceScore;
    private String relationReason;

    public String getEmoji() {
        return pushType == AStockPushType.REALTIME_RISK ? "⚠️" : "🚨";
    }
}
