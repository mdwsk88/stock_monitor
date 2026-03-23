package com.dawei.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营看板快照。
 */
@Data
public class OpsDashboardSnapshot {

    private LocalDateTime generatedAt;
    private String healthLevel;
    private List<String> warnings = new ArrayList<>();
    private MarketSnapshot marketSnapshot;
    private OpsDashboardMetrics metrics;
    private Map<String, Integer> pushTypeDistribution = new LinkedHashMap<>();
    private List<OpsTimelinePoint> timeline = new ArrayList<>();
    private List<OpsRecentPushItem> recentPushes = new ArrayList<>();
    private List<OpsRecentDecisionItem> recentSkippedDecisions = new ArrayList<>();
    private List<OpsStatItem> topSilentReasons = new ArrayList<>();
}
