package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A股公告与宏观主题的共振卡片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AReportResonanceCard {

    private String stockCode;
    private String stockName;
    private String signalSide;
    private int fusionScore;
    private int noticeSignalScore;
    private int macroSignalScore;
    private int eventClusterCount;
    private int supportNoticeCount;
    private String noticeEventType;
    private String noticeTitle;
    private String noticeAnalysisHint;
    private String macroThemeName;
    private String macroEventType;
    private String macroTitle;
    private String macroSummary;
    private String relationReason;

    public String getFusionLevel() {
        if (fusionScore >= 130) {
            return "强共振";
        }
        if (fusionScore >= 100) {
            return "高共振";
        }
        if (fusionScore >= 80) {
            return "弱共振";
        }
        return "观察";
    }

    public String getColorTag() {
        if ("利空".equals(signalSide)) {
            return "warning";
        }
        if (fusionScore >= 130) {
            return "warning";
        }
        if (fusionScore >= 100) {
            return "info";
        }
        if (fusionScore >= 80) {
            return "success";
        }
        return "comment";
    }
}
