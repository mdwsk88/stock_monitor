package com.dawei.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近静默决策项。
 */
@Data
public class OpsRecentDecisionItem {

    private LocalDateTime decidedAt;
    private String sendStatus;
    private String pushType;
    private String title;
    private String stockCode;
    private String stockName;
    private String marketState;
    private String decisionReason;
}
