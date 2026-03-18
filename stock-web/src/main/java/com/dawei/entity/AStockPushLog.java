package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A股实时推送日志
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_stock_push_log")
public class AStockPushLog {

    private String id;
    private String pushKey;
    private String stockCode;
    private String stockName;
    private String pushType;
    private String signalSide;
    private Integer signalScore;
    private String eventType;
    private String title;
    private String macroThemeName;
    private Integer resonanceScore;
    private String decisionReason;
    private LocalDateTime pushedAt;
    private LocalDateTime createTime;
}
