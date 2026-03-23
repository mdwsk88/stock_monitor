package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A股实时推送决策日志
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_stock_push_decision_log")
public class AStockPushDecisionLog {

    private String id;
    private String pushKey;
    private String stockCode;
    private String stockName;
    private String pushType;
    private String signalSide;
    private Integer signalScore;
    private String eventType;
    private String title;
    private String marketState;
    private Integer shouldSendRealtime;
    private Integer critical;
    private Integer withinTradingSession;
    private Integer cooldownHit;
    private String sendStatus;
    private String failureReason;
    private String macroThemeName;
    private Integer resonanceScore;
    private String positionLabel;
    private String decisionReason;
    private LocalDateTime eventTime;
    private LocalDateTime decidedAt;
    private LocalDateTime createTime;
}
