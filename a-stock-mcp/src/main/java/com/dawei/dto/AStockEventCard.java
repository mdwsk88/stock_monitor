package com.dawei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockEventCard {

    private String stockCode;
    private String stockName;
    private String representativeTitle;
    private String eventType;
    private String signalSide;
    private Integer signalScore;
    private Integer rawSignalScore;
    private Integer stockAggregateScore;
    private Integer fusionScore;
    private String scoreType;
    private String clusterKey;
    private String tag;
    private LocalDateTime latestPubDate;
    private Integer supportNoticeCount;
    private Integer eventClusterCount;
    private String macroThemeName;
    private Integer macroSignalScore;
    private String relationReason;
    private String relatedTitles;
    private String analysisHint;
    private String scoreLabel;
    private String scoreWindow;
    private String scoreComparisonNote;
}
