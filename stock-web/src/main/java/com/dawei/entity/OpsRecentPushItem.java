package com.dawei.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近推送项。
 */
@Data
public class OpsRecentPushItem {

    private LocalDateTime pushedAt;
    private String pushType;
    private String signalSide;
    private Integer signalScore;
    private String title;
    private String stockCode;
    private String stockName;
    private String macroThemeName;
    private String decisionReason;
}
