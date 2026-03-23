package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.mapper.AStockPushDecisionLogMapper;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.service.AStockPushLogService;
import com.dawei.service.MarketStateService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A股实时推送健康巡检与健康告警。
 */
@Slf4j
@Service
public class AStockPushHealthAlertService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PUSH_KEY = "push-health|silence";

    private final AStockRssMapper aStockRssMapper;
    private final AStockPushDecisionLogMapper aStockPushDecisionLogMapper;
    private final AStockPushLogMapper aStockPushLogMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final AStockPushLogService aStockPushLogService;
    private final MarketStateService marketStateService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;

    public AStockPushHealthAlertService(AStockRssMapper aStockRssMapper,
                                        AStockPushDecisionLogMapper aStockPushDecisionLogMapper,
                                        AStockPushLogMapper aStockPushLogMapper,
                                        MacroThemeEventMapper macroThemeEventMapper,
                                        AStockPushLogService aStockPushLogService,
                                        MarketStateService marketStateService,
                                        WeComApi weComApi,
                                        StockFilterConfig filterConfig) {
        this.aStockRssMapper = aStockRssMapper;
        this.aStockPushDecisionLogMapper = aStockPushDecisionLogMapper;
        this.aStockPushLogMapper = aStockPushLogMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.aStockPushLogService = aStockPushLogService;
        this.marketStateService = marketStateService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
    }

    public AStockPushHealthCheckResult inspectAndPushIfNeeded() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(filterConfig.getRealtimeHealthWindowMinutes());
        MarketSnapshot marketSnapshot = marketStateService.getLatestSnapshot();
        MarketState marketState = marketSnapshot != null && marketSnapshot.getMarketState() != null
                ? marketSnapshot.getMarketState()
                : MarketState.NEUTRAL;

        int highSignalNoticeCount = countHighSignalNotices(windowStart);
        int hardRiskNoticeCount = countHardRiskNotices(windowStart);
        int decisionCount = countDecisionLogs(windowStart, null, null);
        int sentCount = countDecisionLogs(windowStart, "SENT", null);
        int skippedCount = countDecisionLogs(windowStart, "SKIPPED", null);
        int failedCount = countDecisionLogs(windowStart, "FAILED", null);
        int riskSentCount = countDecisionLogs(windowStart, "SENT", AStockPushType.REALTIME_RISK.name());
        int macroRiskEventCount = countMacroEvents(windowStart, "利空", filterConfig.getMacroRealtimeRiskThresholdDefensive());
        int macroOpportunityEventCount = countMacroEvents(windowStart, "利多", filterConfig.getMacroRealtimeOpportunityThresholdRiskOn());
        int macroSentCount = countPushLogs(windowStart, null);
        int macroRiskSentCount = countPushLogs(windowStart, AStockPushType.MACRO_REALTIME_RISK.name());

        AStockPushHealthCheckResult result = new AStockPushHealthCheckResult();
        result.setWindowStart(windowStart);
        result.setWindowEnd(now);
        result.setMarketState(marketState);
        populateSnapshotHealth(result, marketSnapshot);
        result.setHighSignalNoticeCount(highSignalNoticeCount);
        result.setHardRiskNoticeCount(hardRiskNoticeCount);
        result.setDecisionCount(decisionCount);
        result.setSentCount(sentCount);
        result.setSkippedCount(skippedCount);
        result.setFailedCount(failedCount);
        result.setRiskSentCount(riskSentCount);
        result.setMacroRiskEventCount(macroRiskEventCount);
        result.setMacroOpportunityEventCount(macroOpportunityEventCount);
        result.setMacroSentCount(macroSentCount);
        result.setMacroRiskSentCount(macroRiskSentCount);

        List<String> reasons = buildAlertReasons(result);
        if (reasons.isEmpty()) {
            result.setAlertTriggered(false);
            result.setAlertSummary("A股实时链路健康，当前窗口未触发健康告警");
            return result;
        }

        result.setAlertTriggered(true);
        result.setAlertReason(String.join("；", reasons));
        result.setAlertSummary(buildAlertSummary(result));
        result.setSampleNoticeTitles(loadSampleNoticeTitles(windowStart));
        result.setSampleDecisionReasons(loadSampleDecisionReasons(windowStart));

        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getRealtimeHealthCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(PUSH_KEY, AStockPushType.REALTIME_HEALTH_ALERT, cooldownStart)) {
            log.info("A股实时链路健康告警命中冷却期，summary={}", result.getAlertSummary());
            result.setPushed(false);
            return result;
        }

        try {
            weComApi.sendMarkdownMessage(buildMarkdown(result, marketSnapshot), WeComApi.MarketType.A);
            aStockPushLogService.recordPush(buildPushLog(result, now));
            result.setPushed(true);
            return result;
        } catch (Exception ex) {
            log.error("A股实时链路健康告警推送失败，summary={}, reason={}",
                    result.getAlertSummary(), ex.getMessage(), ex);
            result.setPushed(false);
            return result;
        }
    }

    private void populateSnapshotHealth(AStockPushHealthCheckResult result, MarketSnapshot marketSnapshot) {
        if (result == null || marketSnapshot == null) {
            return;
        }
        result.setSnapshotHealth(marketSnapshot.getSnapshotHealth());
        result.setSnapshotSource(marketSnapshot.getSource());
        result.setSnapshotConsecutiveFailureCount(marketSnapshot.getConsecutiveFailureCount());
        result.setSnapshotBreadthSampleSize(marketSnapshot.getBreadthSampleSize());
        result.setSnapshotFallback(marketSnapshot.isFallback());
        result.setSnapshotStale(marketSnapshot.isStale());
        result.setSnapshotCapturedAt(marketSnapshot.getCapturedAt());
        result.setSnapshotLastSuccessAt(marketSnapshot.getLastSuccessAt());
        result.setSnapshotLastFailureAt(marketSnapshot.getLastFailureAt());
        result.setSnapshotNextRetryAt(marketSnapshot.getNextRetryAt());
        result.setSnapshotLastFailureReason(marketSnapshot.getLastFailureReason());
    }

    private int countHighSignalNotices(LocalDateTime windowStart) {
        return Math.toIntExact(aStockRssMapper.selectCount(new QueryWrapper<AStockRss>()
                .ge("pub_date", windowStart)
                .ge("signal_score", filterConfig.getARealtimeSignalThreshold())));
    }

    private int countHardRiskNotices(LocalDateTime windowStart) {
        return Math.toIntExact(aStockRssMapper.selectCount(new QueryWrapper<AStockRss>()
                .ge("pub_date", windowStart)
                .eq("signal_side", "利空")
                .ge("signal_score", filterConfig.getARealtimeRiskThresholdDefensive())));
    }

    private int countDecisionLogs(LocalDateTime windowStart, String sendStatus, String pushType) {
        QueryWrapper<AStockPushDecisionLog> queryWrapper = new QueryWrapper<AStockPushDecisionLog>()
                .ge("decided_at", windowStart);
        if (StringUtils.isNotBlank(sendStatus)) {
            queryWrapper.eq("send_status", sendStatus);
        }
        if (StringUtils.isNotBlank(pushType)) {
            queryWrapper.eq("push_type", pushType);
        }
        return Math.toIntExact(aStockPushDecisionLogMapper.selectCount(queryWrapper));
    }

    private int countMacroEvents(LocalDateTime windowStart, String signalSide, int threshold) {
        QueryWrapper<MacroThemeEvent> queryWrapper = new QueryWrapper<MacroThemeEvent>()
                .ge("pub_date", windowStart)
                .eq("signal_side", signalSide)
                .ge("signal_score", threshold);
        return Math.toIntExact(macroThemeEventMapper.selectCount(queryWrapper));
    }

    private int countPushLogs(LocalDateTime windowStart, String pushType) {
        QueryWrapper<AStockPushLog> queryWrapper = new QueryWrapper<AStockPushLog>()
                .ge("pushed_at", windowStart)
                .in("push_type", List.of(
                        AStockPushType.MACRO_REALTIME_OPPORTUNITY.name(),
                        AStockPushType.MACRO_REALTIME_RISK.name()
                ));
        if (StringUtils.isNotBlank(pushType)) {
            queryWrapper.eq("push_type", pushType);
        }
        return Math.toIntExact(aStockPushLogMapper.selectCount(queryWrapper));
    }

    private List<String> buildAlertReasons(AStockPushHealthCheckResult result) {
        List<String> reasons = new ArrayList<>();
        if (result.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED) {
            reasons.add("市场快照已失联，状态机正在依赖回退快照");
        } else if (result.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            reasons.add("市场快照处于回退中，状态机正在使用缓存快照");
        }
        if (sourceContains(result, "NO_BREADTH")) {
            reasons.add("市场宽度当前不可用，状态机已退化为指数快照");
        } else if (sourceContains(result, "SAMPLE_BREADTH")
                && safeInt(result.getSnapshotBreadthSampleSize()) > 0
                && safeInt(result.getSnapshotBreadthSampleSize()) < filterConfig.getMarketBreadthSampleWarnThreshold()) {
            reasons.add("市场宽度当前依赖样本代理，且样本数偏低");
        }
        if (result.getHardRiskNoticeCount() >= filterConfig.getRealtimeHealthHardRiskCountThreshold()
                && result.getRiskSentCount() == 0) {
            reasons.add("硬风险公告已堆积，但实时风险推送为 0");
        }
        if (result.getHighSignalNoticeCount() >= filterConfig.getRealtimeHealthHighSignalCountThreshold()
                && result.getSentCount() == 0) {
            reasons.add("高分公告持续入库，但实时推送为 0");
        }
        double skippedRatio = result.getDecisionCount() <= 0
                ? 0d
                : result.getSkippedCount() * 1.0d / result.getDecisionCount();
        if (result.getDecisionCount() >= filterConfig.getRealtimeHealthDecisionCountThreshold()
                && result.getSentCount() == 0
                && skippedRatio >= filterConfig.getRealtimeHealthSkippedRatioThreshold()) {
            reasons.add(String.format("决策层进入高静默区，跳过占比 %.0f%%", skippedRatio * 100));
        }
        if (result.getFailedCount() >= filterConfig.getRealtimeHealthFailureCountThreshold()
                && result.getSentCount() == 0) {
            reasons.add("发送失败次数偏高，实时链路可能异常");
        }
        if (result.getMacroRiskEventCount() >= filterConfig.getRealtimeHealthMacroRiskCountThreshold()
                && result.getMacroRiskSentCount() == 0) {
            reasons.add("宏观风险快讯已堆积，但宏观实时风险推送为 0");
        }
        if (result.getMarketState() != null
                && result.getMarketState().isRiskOn()
                && result.getMacroOpportunityEventCount() >= filterConfig.getRealtimeHealthMacroOpportunityCountThreshold()
                && result.getMacroSentCount() == 0) {
            reasons.add("进攻态下宏观利多快讯密集，但宏观实时推送为 0");
        }
        return reasons;
    }

    private String buildAlertSummary(AStockPushHealthCheckResult result) {
        List<String> summaryParts = new ArrayList<>();
        if (result.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED
                || result.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            summaryParts.add("市场快照" + snapshotHealthLabel(result.getSnapshotHealth())
                    + "，数据源 " + StringUtils.defaultIfBlank(result.getSnapshotSource(), "--")
                    + "，连续失败 " + safeInt(result.getSnapshotConsecutiveFailureCount()) + " 次");
        } else if (sourceContains(result, "NO_BREADTH")) {
            summaryParts.add("市场宽度缺失，状态机当前退化为指数快照");
        } else if (sourceContains(result, "SAMPLE_BREADTH")
                && safeInt(result.getSnapshotBreadthSampleSize()) > 0
                && safeInt(result.getSnapshotBreadthSampleSize()) < filterConfig.getMarketBreadthSampleWarnThreshold()) {
            summaryParts.add("市场宽度仅使用样本代理，当前样本 " + safeInt(result.getSnapshotBreadthSampleSize()) + " 只");
        }
        summaryParts.add("过去 " + filterConfig.getRealtimeHealthWindowMinutes() + " 分钟内高分公告 "
                + result.getHighSignalNoticeCount() + " 条，硬风险 "
                + result.getHardRiskNoticeCount() + " 条，宏观风险 "
                + result.getMacroRiskEventCount() + " 条，但实时已发送仅 "
                + result.getSentCount() + " 条，宏观已发送 "
                + result.getMacroSentCount() + " 条");
        return String.join("；", summaryParts);
    }

    private List<String> loadSampleNoticeTitles(LocalDateTime windowStart) {
        return aStockRssMapper.selectList(new QueryWrapper<AStockRss>()
                        .select("stock_name", "stock_code", "title", "signal_side", "signal_score")
                        .ge("pub_date", windowStart)
                        .ge("signal_score", filterConfig.getARealtimeSignalThreshold())
                        .orderByDesc("signal_score")
                        .orderByDesc("pub_date")
                        .last("LIMIT 3"))
                .stream()
                .map(notice -> notice.getStockName() + "(" + notice.getStockCode() + ") "
                        + "[" + StringUtils.defaultString(notice.getSignalSide(), "未知") + "/"
                        + safeInt(notice.getSignalScore()) + "分] "
                        + StringUtils.abbreviate(StringUtils.defaultString(notice.getTitle()), 50))
                .collect(Collectors.toList());
    }

    private List<String> loadSampleDecisionReasons(LocalDateTime windowStart) {
        return aStockPushDecisionLogMapper.selectList(new QueryWrapper<AStockPushDecisionLog>()
                        .select("stock_name", "stock_code", "decision_reason", "send_status")
                        .ge("decided_at", windowStart)
                        .orderByDesc("decided_at")
                        .last("LIMIT 3"))
                .stream()
                .map(logEntry -> logEntry.getStockName() + "(" + logEntry.getStockCode() + ") "
                        + "[" + StringUtils.defaultString(logEntry.getSendStatus(), "UNKNOWN") + "] "
                        + StringUtils.abbreviate(StringUtils.defaultString(logEntry.getDecisionReason(), "无决策原因"), 50))
                .collect(Collectors.toList());
    }

    private String buildMarkdown(AStockPushHealthCheckResult result, MarketSnapshot snapshot) {
        boolean defensive = result.getMarketState() == MarketState.DEFENSIVE;
        String color = defensive ? "warning" : "comment";
        String stateLabel = result.getMarketState() != null ? result.getMarketState().getLabel() : MarketState.NEUTRAL.getLabel();
        String snapshotHealthLabel = snapshotHealthLabel(result.getSnapshotHealth());
        String noticeLines = result.getSampleNoticeTitles().isEmpty()
                ? "> **样本公告**：当前窗口无高分样本\n"
                : result.getSampleNoticeTitles().stream()
                .map(item -> "> - " + item)
                .collect(Collectors.joining("\n")) + "\n";
        String decisionLines = result.getSampleDecisionReasons().isEmpty()
                ? "> **决策样本**：当前窗口无决策样本\n"
                : result.getSampleDecisionReasons().stream()
                .map(item -> "> - " + item)
                .collect(Collectors.joining("\n")) + "\n";
        String marketLine = snapshot == null
                ? "> **市场状态**：<font color=\"" + color + "\">" + stateLabel + "</font>\n"
                : "> **市场状态**：<font color=\"" + color + "\">" + stateLabel + "</font>"
                + " | 上证 " + formatPct(snapshot.getShChangePct())
                + " | 深成 " + formatPct(snapshot.getSzChangePct())
                + " | 创业板 " + formatPct(snapshot.getCybChangePct()) + "\n";
        String snapshotLine = "> **快照链路**："
                + StringUtils.defaultString(snapshotHealthLabel, "--")
                + " | 数据源 " + StringUtils.defaultIfBlank(result.getSnapshotSource(), "--")
                + " | 宽度样本 " + (safeInt(result.getSnapshotBreadthSampleSize()) > 0
                ? safeInt(result.getSnapshotBreadthSampleSize()) + " 只"
                : "--")
                + " | 连续失败 " + safeInt(result.getSnapshotConsecutiveFailureCount()) + "\n";
        String snapshotFailureLine = StringUtils.isBlank(result.getSnapshotLastFailureReason())
                ? ""
                : "> **最近失败**：" + StringUtils.abbreviate(result.getSnapshotLastFailureReason(), 80)
                + " | 最后失败时间 " + formatTime(result.getSnapshotLastFailureAt())
                + " | 下次重试 " + formatTime(result.getSnapshotNextRetryAt()) + "\n";

        return "# 🚨 A股实时链路健康告警\n\n"
                + marketLine
                + snapshotLine
                + snapshotFailureLine
                + "> **巡检窗口**：" + result.getWindowStart().format(TIME_FORMATTER)
                + " ~ " + result.getWindowEnd().format(TIME_FORMATTER) + "\n"
                + "> **结论**：" + result.getAlertSummary() + "\n"
                + "> **触发原因**：" + result.getAlertReason() + "\n"
                + "> **决策分布**：总决策 " + result.getDecisionCount()
                + " | 已发送 " + result.getSentCount()
                + " | 已跳过 " + result.getSkippedCount()
                + " | 失败 " + result.getFailedCount()
                + " | 风险已发送 " + result.getRiskSentCount() + "\n"
                + "> **宏观分布**：风险事件 " + result.getMacroRiskEventCount()
                + " | 机会事件 " + result.getMacroOpportunityEventCount()
                + " | 宏观已发送 " + result.getMacroSentCount()
                + " | 宏观风险已发送 " + result.getMacroRiskSentCount() + "\n"
                + noticeLines
                + decisionLines
                + "> **执行建议**：优先检查市场快照上游、实时窗口判定、状态机降级、冷却策略和企业微信发送链路。\n\n"
                + "<font color=\"comment\">健康告警基于决策日志、公告入库与市场快照状态，目的是暴露链路失声和状态机退化。</font>";
    }

    private AStockPushLog buildPushLog(AStockPushHealthCheckResult result, LocalDateTime now) {
        AStockPushLog pushLog = new AStockPushLog();
        pushLog.setId(UUID.randomUUID().toString().replace("-", ""));
        pushLog.setPushKey(PUSH_KEY);
        pushLog.setPushType(AStockPushType.REALTIME_HEALTH_ALERT.name());
        pushLog.setSignalSide(result.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED
                || result.getMarketState() == MarketState.DEFENSIVE ? "利空" : "中性");
        pushLog.setSignalScore(Math.max(safeInt(result.getHighSignalNoticeCount()),
                safeInt(result.getSnapshotConsecutiveFailureCount())));
        pushLog.setEventType("实时链路健康告警");
        pushLog.setTitle(result.getAlertSummary());
        pushLog.setDecisionReason(result.getAlertReason());
        pushLog.setPushedAt(now);
        pushLog.setCreateTime(now);
        return pushLog;
    }

    private boolean sourceContains(AStockPushHealthCheckResult result, String marker) {
        return result != null
                && StringUtils.containsIgnoreCase(StringUtils.defaultString(result.getSnapshotSource()), marker);
    }

    private String snapshotHealthLabel(MarketSnapshotHealth snapshotHealth) {
        return snapshotHealth != null ? snapshotHealth.getLabel() : "--";
    }

    private String formatPct(double value) {
        return String.format("%+.2f%%", value);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "--" : value.format(TIME_FORMATTER);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
