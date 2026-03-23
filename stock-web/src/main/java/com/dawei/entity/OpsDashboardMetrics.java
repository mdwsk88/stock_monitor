package com.dawei.entity;

import lombok.Data;

/**
 * 运营看板核心指标。
 */
@Data
public class OpsDashboardMetrics {

    private int healthWindowMinutes;
    private int highSignalNoticeCount;
    private int hardRiskNoticeCount;
    private int decisionCount;
    private int sentCount;
    private int skippedCount;
    private int failedCount;
    private int riskSentCount;
    private int macroRiskEventCount;
    private int macroOpportunityEventCount;
    private int macroSentCount;
    private int macroRiskSentCount;
    private int marketPulseCount;
    private int healthAlertCount;
    private double skippedRatio;
}
