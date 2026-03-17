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
    private String clusterKey;
    private String tag;
    private LocalDateTime latestPubDate;
    private Integer supportNoticeCount;
    private String relatedTitles;
    private String analysisHint;
}
