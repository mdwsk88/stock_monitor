package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A股机会榜身位洞察。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AReportOpportunityInsight {

    private String stockCode;
    private String stockName;
    private String positionLabel;
    private String positionReason;
    private String tradeHint;
    private int convictionScore;
    private boolean resonanceSupported;

    public String getColorTag() {
        return switch (positionLabel) {
            case "领军核心" -> "warning";
            case "高弹性跟风" -> "info";
            default -> "comment";
        };
    }
}
