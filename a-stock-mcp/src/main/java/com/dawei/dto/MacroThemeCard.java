package com.dawei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroThemeCard {

    private String themeName;
    private String representativeTitle;
    private String summary;
    private String signalSide;
    private Integer signalScore;
    private Integer importanceLevel;
    private LocalDateTime latestPubDate;
    private Integer supportEventCount;
    private Integer mappedStockCount;
    private String mappedStocks;
    private String relatedTitles;
    private String analysisHint;
}
