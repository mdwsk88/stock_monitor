package com.dawei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResolveResult {

    private String stockCode;
    private String stockName;
    private String matchType;
    private Integer confidence;
    private Integer recentHighValueNoticeCount;
    private Integer topSignalScore;
    private LocalDateTime latestPubDate;
}
