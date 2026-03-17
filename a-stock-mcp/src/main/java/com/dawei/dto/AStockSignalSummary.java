package com.dawei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockSignalSummary {

    private String stockCode;
    private String stockName;
    private String query;
    private Integer lookbackDays;
    private String dominantSignalSide;
    private Integer bullishEventCount;
    private Integer bearishEventCount;
    private Integer neutralEventCount;
    private Integer highValueNoticeCount;
    private Integer eventClusterCount;
    private Integer topSignalScore;
    private LocalDateTime latestPubDate;
    private String analysisHint;
    private List<AStockEventCard> topEvents;
}
