package com.dawei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResonanceStockCard {

    private String themeName;
    private String stockCode;
    private String stockName;
    private Integer candidateScore;
    private Integer hitCount;
    private LocalDateTime latestPubDate;
    private Integer themeSignalScore;
    private String themeSignalSide;
    private String relatedEventTitle;
    private String reason;
    private String analysisHint;
}
