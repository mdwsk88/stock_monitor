package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A股实时推送健康巡检结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockPushHealthCheckResult {

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private MarketState marketState;
    private MarketSnapshotHealth snapshotHealth;
    private String snapshotSource;
    private Integer snapshotConsecutiveFailureCount;
    private Integer snapshotBreadthSampleSize;
    private boolean snapshotFallback;
    private boolean snapshotStale;
    private LocalDateTime snapshotCapturedAt;
    private LocalDateTime snapshotLastSuccessAt;
    private LocalDateTime snapshotLastFailureAt;
    private LocalDateTime snapshotNextRetryAt;
    private String snapshotLastFailureReason;
    private Integer highSignalNoticeCount;
    private Integer hardRiskNoticeCount;
    private Integer decisionCount;
    private Integer sentCount;
    private Integer skippedCount;
    private Integer failedCount;
    private Integer riskSentCount;
    private Integer macroRiskEventCount;
    private Integer macroOpportunityEventCount;
    private Integer macroSentCount;
    private Integer macroRiskSentCount;
    private boolean alertTriggered;
    private boolean pushed;
    private String alertSummary;
    private String alertReason;
    private List<String> sampleNoticeTitles = new ArrayList<>();
    private List<String> sampleDecisionReasons = new ArrayList<>();
}
