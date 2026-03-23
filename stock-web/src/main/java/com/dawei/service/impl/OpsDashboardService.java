package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.OpsDashboardMetrics;
import com.dawei.entity.OpsDashboardSnapshot;
import com.dawei.entity.OpsRecentDecisionItem;
import com.dawei.entity.OpsRecentPushItem;
import com.dawei.entity.OpsStatItem;
import com.dawei.entity.OpsTimelinePoint;
import com.dawei.mapper.AStockPushDecisionLogMapper;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.service.MarketStateService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运营看板聚合服务。
 */
@Service
public class OpsDashboardService {

    private static final int LOOKBACK_HOURS = 24;
    private static final int TIMELINE_HOURS = 12;
    private static final int RECENT_PUSH_LIMIT = 12;
    private static final int RECENT_DECISION_LIMIT = 12;
    private static final int REASON_LIMIT = 8;
    private static final int QUERY_LIMIT = 3000;

    private final AStockRssMapper aStockRssMapper;
    private final AStockPushDecisionLogMapper aStockPushDecisionLogMapper;
    private final AStockPushLogMapper aStockPushLogMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final MarketStateService marketStateService;
    private final StockFilterConfig filterConfig;

    public OpsDashboardService(AStockRssMapper aStockRssMapper,
                               AStockPushDecisionLogMapper aStockPushDecisionLogMapper,
                               AStockPushLogMapper aStockPushLogMapper,
                               MacroThemeEventMapper macroThemeEventMapper,
                               MarketStateService marketStateService,
                               StockFilterConfig filterConfig) {
        this.aStockRssMapper = aStockRssMapper;
        this.aStockPushDecisionLogMapper = aStockPushDecisionLogMapper;
        this.aStockPushLogMapper = aStockPushLogMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.marketStateService = marketStateService;
        this.filterConfig = filterConfig;
    }

    public OpsDashboardSnapshot buildSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lookbackStart = now.minusHours(LOOKBACK_HOURS);
        LocalDateTime healthWindowStart = now.minusMinutes(filterConfig.getRealtimeHealthWindowMinutes());
        LocalDateTime timelineStart = now.minusHours(TIMELINE_HOURS).truncatedTo(ChronoUnit.HOURS);

        List<AStockRss> notices = loadRecentNotices(lookbackStart);
        List<AStockPushDecisionLog> decisions = loadRecentDecisions(lookbackStart);
        List<AStockPushLog> pushLogs = loadRecentPushLogs(lookbackStart);
        List<MacroThemeEvent> macroEvents = loadRecentMacroEvents(lookbackStart);
        MarketSnapshot marketSnapshot = marketStateService.getLatestSnapshot();

        OpsDashboardMetrics metrics = buildMetrics(notices, decisions, pushLogs, macroEvents, healthWindowStart);

        OpsDashboardSnapshot snapshot = new OpsDashboardSnapshot();
        snapshot.setGeneratedAt(now);
        snapshot.setMarketSnapshot(marketSnapshot);
        snapshot.setMetrics(metrics);
        snapshot.setPushTypeDistribution(buildPushTypeDistribution(pushLogs));
        snapshot.setTimeline(buildTimeline(notices, decisions, pushLogs, macroEvents, timelineStart, now));
        snapshot.setRecentPushes(buildRecentPushes(pushLogs));
        snapshot.setRecentSkippedDecisions(buildRecentSkippedDecisions(decisions));
        snapshot.setTopSilentReasons(buildTopSilentReasons(decisions));
        snapshot.setWarnings(buildWarnings(metrics, marketSnapshot));
        snapshot.setHealthLevel(resolveHealthLevel(snapshot.getWarnings(), metrics, marketSnapshot));
        return snapshot;
    }

    private List<AStockRss> loadRecentNotices(LocalDateTime startTime) {
        return aStockRssMapper.selectList(new QueryWrapper<AStockRss>()
                .select("stock_code", "stock_name", "title", "signal_side", "signal_score", "pub_date")
                .ge("pub_date", startTime)
                .orderByDesc("pub_date")
                .last("LIMIT " + QUERY_LIMIT));
    }

    private List<AStockPushDecisionLog> loadRecentDecisions(LocalDateTime startTime) {
        return aStockPushDecisionLogMapper.selectList(new QueryWrapper<AStockPushDecisionLog>()
                .select("stock_code", "stock_name", "title", "push_type", "send_status",
                        "market_state", "decision_reason", "decided_at")
                .ge("decided_at", startTime)
                .orderByDesc("decided_at")
                .last("LIMIT " + QUERY_LIMIT));
    }

    private List<AStockPushLog> loadRecentPushLogs(LocalDateTime startTime) {
        return aStockPushLogMapper.selectList(new QueryWrapper<AStockPushLog>()
                .select("push_type", "signal_side", "signal_score", "title", "stock_code",
                        "stock_name", "macro_theme_name", "decision_reason", "pushed_at")
                .ge("pushed_at", startTime)
                .orderByDesc("pushed_at")
                .last("LIMIT " + QUERY_LIMIT));
    }

    private List<MacroThemeEvent> loadRecentMacroEvents(LocalDateTime startTime) {
        return macroThemeEventMapper.selectList(new QueryWrapper<MacroThemeEvent>()
                .select("theme_name", "event_type", "title", "summary", "signal_side", "signal_score", "pub_date")
                .ge("pub_date", startTime)
                .orderByDesc("pub_date")
                .last("LIMIT " + QUERY_LIMIT));
    }

    private OpsDashboardMetrics buildMetrics(List<AStockRss> notices,
                                             List<AStockPushDecisionLog> decisions,
                                             List<AStockPushLog> pushLogs,
                                             List<MacroThemeEvent> macroEvents,
                                             LocalDateTime healthWindowStart) {
        List<AStockRss> windowNotices = notices.stream()
                .filter(item -> item.getPubDate() != null && !item.getPubDate().isBefore(healthWindowStart))
                .toList();
        List<AStockPushDecisionLog> windowDecisions = decisions.stream()
                .filter(item -> item.getDecidedAt() != null && !item.getDecidedAt().isBefore(healthWindowStart))
                .toList();
        List<AStockPushLog> windowPushLogs = pushLogs.stream()
                .filter(item -> item.getPushedAt() != null && !item.getPushedAt().isBefore(healthWindowStart))
                .toList();
        List<MacroThemeEvent> windowMacroEvents = macroEvents.stream()
                .filter(item -> item.getPubDate() != null && !item.getPubDate().isBefore(healthWindowStart))
                .toList();

        OpsDashboardMetrics metrics = new OpsDashboardMetrics();
        metrics.setHealthWindowMinutes(filterConfig.getRealtimeHealthWindowMinutes());
        metrics.setHighSignalNoticeCount((int) windowNotices.stream()
                .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getARealtimeSignalThreshold())
                .count());
        metrics.setHardRiskNoticeCount((int) windowNotices.stream()
                .filter(item -> "利空".equals(item.getSignalSide()))
                .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getARealtimeRiskThresholdDefensive())
                .count());
        metrics.setDecisionCount(windowDecisions.size());
        metrics.setSentCount((int) windowDecisions.stream().filter(item -> "SENT".equals(item.getSendStatus())).count());
        metrics.setSkippedCount((int) windowDecisions.stream().filter(item -> "SKIPPED".equals(item.getSendStatus())).count());
        metrics.setFailedCount((int) windowDecisions.stream().filter(item -> "FAILED".equals(item.getSendStatus())).count());
        metrics.setRiskSentCount((int) windowDecisions.stream()
                .filter(item -> "SENT".equals(item.getSendStatus()))
                .filter(item -> AStockPushType.REALTIME_RISK.name().equals(item.getPushType()))
                .count());
        metrics.setMacroRiskEventCount((int) windowMacroEvents.stream()
                .filter(item -> "利空".equals(item.getSignalSide()))
                .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getMacroRealtimeRiskThresholdDefensive())
                .count());
        metrics.setMacroOpportunityEventCount((int) windowMacroEvents.stream()
                .filter(item -> "利多".equals(item.getSignalSide()))
                .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getMacroRealtimeOpportunityThresholdRiskOn())
                .count());
        metrics.setMacroSentCount((int) windowPushLogs.stream()
                .filter(item -> isMacroPush(item.getPushType()))
                .count());
        metrics.setMacroRiskSentCount((int) windowPushLogs.stream()
                .filter(item -> AStockPushType.MACRO_REALTIME_RISK.name().equals(item.getPushType()))
                .count());
        metrics.setMarketPulseCount((int) windowPushLogs.stream()
                .filter(item -> AStockPushType.MARKET_PULSE_OPPORTUNITY.name().equals(item.getPushType())
                        || AStockPushType.MARKET_PULSE_RISK.name().equals(item.getPushType()))
                .count());
        metrics.setHealthAlertCount((int) windowPushLogs.stream()
                .filter(item -> AStockPushType.REALTIME_HEALTH_ALERT.name().equals(item.getPushType()))
                .count());
        metrics.setSkippedRatio(metrics.getDecisionCount() <= 0
                ? 0d
                : round(metrics.getSkippedCount() * 1.0d / metrics.getDecisionCount()));
        return metrics;
    }

    private Map<String, Integer> buildPushTypeDistribution(List<AStockPushLog> pushLogs) {
        return pushLogs.stream()
                .filter(Objects::nonNull)
                .map(AStockPushLog::getPushType)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.summingInt(item -> 1)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<OpsTimelinePoint> buildTimeline(List<AStockRss> notices,
                                                 List<AStockPushDecisionLog> decisions,
                                                 List<AStockPushLog> pushLogs,
                                                 List<MacroThemeEvent> macroEvents,
                                                 LocalDateTime timelineStart,
                                                 LocalDateTime now) {
        List<OpsTimelinePoint> points = new ArrayList<>();
        LocalDateTime cursor = timelineStart;
        LocalDateTime end = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        while (cursor.isBefore(end)) {
            LocalDateTime bucketStart = cursor;
            LocalDateTime bucketEnd = cursor.plusHours(1);
            OpsTimelinePoint point = new OpsTimelinePoint();
            point.setBucketStart(bucketStart);
            point.setHighSignalNoticeCount((int) notices.stream()
                    .filter(item -> withinBucket(item.getPubDate(), bucketStart, bucketEnd))
                    .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getARealtimeSignalThreshold())
                    .count());
            point.setDecisionCount((int) decisions.stream()
                    .filter(item -> withinBucket(item.getDecidedAt(), bucketStart, bucketEnd))
                    .count());
            point.setPushCount((int) pushLogs.stream()
                    .filter(item -> withinBucket(item.getPushedAt(), bucketStart, bucketEnd))
                    .count());
            point.setMacroEventCount((int) macroEvents.stream()
                    .filter(item -> withinBucket(item.getPubDate(), bucketStart, bucketEnd))
                    .filter(item -> safeInt(item.getSignalScore()) >= filterConfig.getMacroShadowSignalThreshold())
                    .count());
            points.add(point);
            cursor = bucketEnd;
        }
        return points;
    }

    private List<OpsRecentPushItem> buildRecentPushes(List<AStockPushLog> pushLogs) {
        return pushLogs.stream()
                .limit(RECENT_PUSH_LIMIT)
                .map(logEntry -> {
                    OpsRecentPushItem item = new OpsRecentPushItem();
                    item.setPushedAt(logEntry.getPushedAt());
                    item.setPushType(logEntry.getPushType());
                    item.setSignalSide(logEntry.getSignalSide());
                    item.setSignalScore(logEntry.getSignalScore());
                    item.setTitle(logEntry.getTitle());
                    item.setStockCode(logEntry.getStockCode());
                    item.setStockName(logEntry.getStockName());
                    item.setMacroThemeName(logEntry.getMacroThemeName());
                    item.setDecisionReason(logEntry.getDecisionReason());
                    return item;
                })
                .toList();
    }

    private List<OpsRecentDecisionItem> buildRecentSkippedDecisions(List<AStockPushDecisionLog> decisions) {
        return decisions.stream()
                .filter(item -> "SKIPPED".equals(item.getSendStatus()))
                .limit(RECENT_DECISION_LIMIT)
                .map(logEntry -> {
                    OpsRecentDecisionItem item = new OpsRecentDecisionItem();
                    item.setDecidedAt(logEntry.getDecidedAt());
                    item.setSendStatus(logEntry.getSendStatus());
                    item.setPushType(logEntry.getPushType());
                    item.setTitle(logEntry.getTitle());
                    item.setStockCode(logEntry.getStockCode());
                    item.setStockName(logEntry.getStockName());
                    item.setMarketState(logEntry.getMarketState());
                    item.setDecisionReason(logEntry.getDecisionReason());
                    return item;
                })
                .toList();
    }

    private List<OpsStatItem> buildTopSilentReasons(List<AStockPushDecisionLog> decisions) {
        return decisions.stream()
                .filter(item -> "SKIPPED".equals(item.getSendStatus()))
                .map(item -> StringUtils.defaultIfBlank(item.getDecisionReason(), "未记录原因"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(item -> 1)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(REASON_LIMIT)
                .map(entry -> new OpsStatItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<String> buildWarnings(OpsDashboardMetrics metrics, MarketSnapshot marketSnapshot) {
        List<String> warnings = new ArrayList<>();
        if (metrics.getHighSignalNoticeCount() >= filterConfig.getRealtimeHealthHighSignalCountThreshold()
                && metrics.getSentCount() == 0) {
            warnings.add("高分公告在健康窗口内持续入库，但实时推送仍为 0。");
        }
        if (metrics.getHardRiskNoticeCount() >= filterConfig.getRealtimeHealthHardRiskCountThreshold()
                && metrics.getRiskSentCount() == 0) {
            warnings.add("硬风险公告已经堆积，但实时风险预警没有发声。");
        }
        if (metrics.getMacroRiskEventCount() >= filterConfig.getRealtimeHealthMacroRiskCountThreshold()
                && metrics.getMacroRiskSentCount() == 0) {
            warnings.add("宏观高分利空快讯出现堆积，但宏观实时风险推送为 0。");
        }
        if (marketSnapshot != null
                && marketSnapshot.getMarketState() != null
                && marketSnapshot.getMarketState().isRiskOn()
                && metrics.getMacroOpportunityEventCount() >= filterConfig.getRealtimeHealthMacroOpportunityCountThreshold()
                && metrics.getMacroSentCount() == 0) {
            warnings.add("进攻态下宏观利多快讯密集，但宏观实时机会推送没有放行。");
        }
        if (metrics.getDecisionCount() >= filterConfig.getRealtimeHealthDecisionCountThreshold()
                && metrics.getSentCount() == 0
                && metrics.getSkippedRatio() >= filterConfig.getRealtimeHealthSkippedRatioThreshold()) {
            warnings.add("决策层进入高静默区，跳过占比过高，建议检查阈值与降级规则。");
        }
        if (metrics.getFailedCount() >= filterConfig.getRealtimeHealthFailureCountThreshold()) {
            warnings.add("发送失败次数偏高，需要优先检查企业微信链路与重试策略。");
        }
        if (marketSnapshot != null && marketSnapshot.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED) {
            warnings.add("市场快照已失联，状态机正在回退；连续失败 "
                    + marketSnapshot.getConsecutiveFailureCount()
                    + " 次，最近失败时间 "
                    + formatTime(marketSnapshot.getLastFailureAt()) + "。");
        } else if (marketSnapshot != null && marketSnapshot.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            warnings.add("市场快照当前处于缓存回退态；连续失败 "
                    + marketSnapshot.getConsecutiveFailureCount()
                    + " 次，下次自动重试不早于 "
                    + formatTime(marketSnapshot.getNextRetryAt()) + "。");
        } else if (marketSnapshot != null
                && marketSnapshot.getCapturedAt() != null
                && marketSnapshot.getCapturedAt().isBefore(LocalDateTime.now()
                .minusMinutes(filterConfig.getMarketSnapshotRefreshMinutes() * 2L))) {
            warnings.add("市场状态快照已经偏旧，建议手动刷新市场快照。");
        }
        if (marketSnapshot != null && StringUtils.containsIgnoreCase(StringUtils.defaultString(marketSnapshot.getSource()), "NO_BREADTH")) {
            warnings.add("市场宽度当前不可用，状态机退化为指数快照；高潮判定会更保守。");
        } else if (marketSnapshot != null
                && StringUtils.containsIgnoreCase(StringUtils.defaultString(marketSnapshot.getSource()), "SAMPLE_BREADTH")
                && marketSnapshot.getBreadthSampleSize() > 0
                && marketSnapshot.getBreadthSampleSize() < filterConfig.getMarketBreadthSampleWarnThreshold()) {
            warnings.add("市场宽度当前依赖样本代理，样本数仅 "
                    + marketSnapshot.getBreadthSampleSize()
                    + "，情绪宽度判定存在偏差。");
        }
        return warnings;
    }

    private String resolveHealthLevel(List<String> warnings,
                                      OpsDashboardMetrics metrics,
                                      MarketSnapshot marketSnapshot) {
        if (warnings.isEmpty()) {
            return "HEALTHY";
        }
        if (marketSnapshot != null && marketSnapshot.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED) {
            return "CRITICAL";
        }
        if (metrics.getFailedCount() > 0 || warnings.size() >= 3) {
            return "CRITICAL";
        }
        return "WARN";
    }

    private boolean withinBucket(LocalDateTime time, LocalDateTime bucketStart, LocalDateTime bucketEnd) {
        return time != null && !time.isBefore(bucketStart) && time.isBefore(bucketEnd);
    }

    private boolean isMacroPush(String pushType) {
        return AStockPushType.MACRO_REALTIME_OPPORTUNITY.name().equals(pushType)
                || AStockPushType.MACRO_REALTIME_RISK.name().equals(pushType);
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "--" : time.truncatedTo(ChronoUnit.MINUTES).toString().replace('T', ' ');
    }
}
