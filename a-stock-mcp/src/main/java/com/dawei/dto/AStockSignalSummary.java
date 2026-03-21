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
    private Integer aggregateSignalScore;
    private Integer topSignalScore;
    private Integer topRawSignalScore;
    private String dominantSignalSideLabel;
    private LocalDateTime latestPubDate;
    private String bestResonanceThemeName;
    private Integer bestResonanceFusionScore;
    private Integer bestResonanceMacroSignalScore;
    private String bestResonanceMacroTitle;
    private String bestResonanceReason;
    private String analysisHint;
    private String aggregateScoreLabel;
    private String aggregateScoreWindow;
    private String scoreComparisonNote;
    private List<AStockEventCard> topEvents;
}
