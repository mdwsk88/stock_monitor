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
import com.dawei.service.MarketStateService;
import com.dawei.utils.PushLanguageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A股实时推送健康巡检与健康告警。
 */
@Slf4j
@Service
public class AStockPushHealthAlertService {

    private final AStockRssMapper aStockRssMapper;
    private final AStockPushDecisionLogMapper aStockPushDecisionLogMapper;
    private final AStockPushLogMapper aStockPushLogMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final MarketStateService marketStateService;
    private final StockFilterConfig filterConfig;
    private final PushLanguageService pushLanguageService;

    public AStockPushHealthAlertService(AStockRssMapper aStockRssMapper,
                                        AStockPushDecisionLogMapper aStockPushDecisionLogMapper,
                                        AStockPushLogMapper aStockPushLogMapper,
                                        MacroThemeEventMapper macroThemeEventMapper,
                                        MarketStateService marketStateService,
                                        StockFilterConfig filterConfig) {
        this(aStockRssMapper, aStockPushDecisionLogMapper, aStockPushLogMapper, macroThemeEventMapper,
                marketStateService, filterConfig, new PushLanguageService());
    }

    @Autowired
    public AStockPushHealthAlertService(AStockRssMapper aStockRssMapper,
                                        AStockPushDecisionLogMapper aStockPushDecisionLogMapper,
                                        AStockPushLogMapper aStockPushLogMapper,
                                        MacroThemeEventMapper macroThemeEventMapper,
                                        MarketStateService marketStateService,
                                        StockFilterConfig filterConfig,
                                        PushLanguageService pushLanguageService) {
        this.aStockRssMapper = aStockRssMapper;
        this.aStockPushDecisionLogMapper = aStockPushDecisionLogMapper;
        this.aStockPushLogMapper = aStockPushLogMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.marketStateService = marketStateService;
        this.filterConfig = filterConfig;
        this.pushLanguageService = pushLanguageService;
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
            result.setAlertSummary(pushLanguageService.text(
                    "A股实时链路健康，当前窗口未触发健康告警",
                    "The A-share realtime pipeline is healthy. No health alert was triggered in the current window."
            ));
            return result;
        }

        result.setAlertTriggered(true);
        result.setAlertReason(String.join("；", reasons));
        result.setAlertSummary(buildAlertSummary(result));
        result.setSampleNoticeTitles(loadSampleNoticeTitles(windowStart));
        result.setSampleDecisionReasons(loadSampleDecisionReasons(windowStart));
        result.setPushed(false);
        log.warn("A股实时链路健康巡检发现异常，但企业微信推送已关闭。summary={}, reason={}",
                result.getAlertSummary(), result.getAlertReason());
        return result;
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
            reasons.add(pushLanguageService.text("市场快照已失联，状态机正在依赖回退快照", "The market snapshot is disconnected and the state machine is relying on fallback data."));
        } else if (result.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            reasons.add(pushLanguageService.text("市场快照处于回退中，状态机正在使用缓存快照", "The market snapshot is degraded and the state machine is using cached data."));
        }
        if (sourceContains(result, "NO_BREADTH")) {
            reasons.add(pushLanguageService.text("市场宽度当前不可用，状态机已退化为指数快照", "Market breadth is currently unavailable, so the state machine has degraded to index-only mode."));
        } else if (sourceContains(result, "SAMPLE_BREADTH")
                && safeInt(result.getSnapshotBreadthSampleSize()) > 0
                && safeInt(result.getSnapshotBreadthSampleSize()) < filterConfig.getMarketBreadthSampleWarnThreshold()) {
            reasons.add(pushLanguageService.text("市场宽度当前依赖样本代理，且样本数偏低", "Market breadth currently relies on a sample proxy and the sample size is low."));
        }
        if (result.getHardRiskNoticeCount() >= filterConfig.getRealtimeHealthHardRiskCountThreshold()
                && result.getRiskSentCount() == 0) {
            reasons.add(pushLanguageService.text("硬风险公告已堆积，但实时风险推送为 0", "Hard-risk notices have accumulated, but no realtime risk alert has been sent."));
        }
        if (result.getHighSignalNoticeCount() >= filterConfig.getRealtimeHealthHighSignalCountThreshold()
                && result.getSentCount() == 0) {
            reasons.add(pushLanguageService.text("高分公告持续入库，但实时推送为 0", "High-score notices continue to arrive, but realtime pushes remain at zero."));
        }
        double skippedRatio = result.getDecisionCount() <= 0
                ? 0d
                : result.getSkippedCount() * 1.0d / result.getDecisionCount();
        if (result.getDecisionCount() >= filterConfig.getRealtimeHealthDecisionCountThreshold()
                && result.getSentCount() == 0
                && skippedRatio >= filterConfig.getRealtimeHealthSkippedRatioThreshold()) {
            reasons.add(pushLanguageService.text(
                    String.format("决策层进入高静默区，跳过占比 %.0f%%", skippedRatio * 100),
                    String.format("Decision layer entered a high-silence zone, with %.0f%% skipped.", skippedRatio * 100)
            ));
        }
        if (result.getFailedCount() >= filterConfig.getRealtimeHealthFailureCountThreshold()
                && result.getSentCount() == 0) {
            reasons.add(pushLanguageService.text("发送失败次数偏高，实时链路可能异常", "Send failures are elevated and the realtime notification pipeline may be abnormal."));
        }
        if (result.getMacroRiskEventCount() >= filterConfig.getRealtimeHealthMacroRiskCountThreshold()
                && result.getMacroRiskSentCount() == 0) {
            reasons.add(pushLanguageService.text("宏观风险快讯已堆积，但宏观实时风险推送为 0", "Macro risk events have accumulated, but macro realtime risk alerts remain at zero."));
        }
        if (result.getMarketState() != null
                && result.getMarketState().isRiskOn()
                && result.getMacroOpportunityEventCount() >= filterConfig.getRealtimeHealthMacroOpportunityCountThreshold()
                && result.getMacroSentCount() == 0) {
            reasons.add(pushLanguageService.text("进攻态下宏观利多快讯密集，但宏观实时推送为 0", "Bullish macro headlines are dense in a risk-on regime, but macro realtime pushes remain at zero."));
        }
        return reasons;
    }

    private String buildAlertSummary(AStockPushHealthCheckResult result) {
        List<String> summaryParts = new ArrayList<>();
        if (result.getSnapshotHealth() == MarketSnapshotHealth.DISCONNECTED
                || result.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            summaryParts.add(pushLanguageService.text(
                    "市场快照" + snapshotHealthLabel(result.getSnapshotHealth())
                            + "，数据源 " + StringUtils.defaultIfBlank(result.getSnapshotSource(), "--")
                            + "，连续失败 " + safeInt(result.getSnapshotConsecutiveFailureCount()) + " 次",
                    "Market snapshot is " + snapshotHealthLabel(result.getSnapshotHealth())
                            + ", source " + StringUtils.defaultIfBlank(result.getSnapshotSource(), "--")
                            + ", consecutive failures " + safeInt(result.getSnapshotConsecutiveFailureCount())
            ));
        } else if (sourceContains(result, "NO_BREADTH")) {
            summaryParts.add(pushLanguageService.text("市场宽度缺失，状态机当前退化为指数快照", "Market breadth is missing and the state machine is currently degraded to index-only mode."));
        } else if (sourceContains(result, "SAMPLE_BREADTH")
                && safeInt(result.getSnapshotBreadthSampleSize()) > 0
                && safeInt(result.getSnapshotBreadthSampleSize()) < filterConfig.getMarketBreadthSampleWarnThreshold()) {
            summaryParts.add(pushLanguageService.text(
                    "市场宽度仅使用样本代理，当前样本 " + safeInt(result.getSnapshotBreadthSampleSize()) + " 只",
                    "Market breadth is using a sample proxy only, with " + safeInt(result.getSnapshotBreadthSampleSize()) + " names in the sample"
            ));
        }
        summaryParts.add(pushLanguageService.text(
                "过去 " + filterConfig.getRealtimeHealthWindowMinutes() + " 分钟内高分公告 "
                        + result.getHighSignalNoticeCount() + " 条，硬风险 "
                        + result.getHardRiskNoticeCount() + " 条，宏观风险 "
                        + result.getMacroRiskEventCount() + " 条，但实时已发送仅 "
                        + result.getSentCount() + " 条，宏观已发送 "
                        + result.getMacroSentCount() + " 条",
                "In the last " + filterConfig.getRealtimeHealthWindowMinutes() + " minutes, there were "
                        + result.getHighSignalNoticeCount() + " high-score notices, "
                        + result.getHardRiskNoticeCount() + " hard-risk notices, and "
                        + result.getMacroRiskEventCount() + " macro risk events, but only "
                        + result.getSentCount() + " realtime pushes and "
                        + result.getMacroSentCount() + " macro pushes were sent."
        ));
        return String.join(pushLanguageService.text("；", "; "), summaryParts);
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
                        + "[" + pushLanguageService.signalSideLabel(StringUtils.defaultString(notice.getSignalSide(), pushLanguageService.text("未知", "Unknown"))) + "/"
                        + safeInt(notice.getSignalScore()) + pushLanguageService.text("分", "") + "] "
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
                        + StringUtils.abbreviate(StringUtils.defaultString(logEntry.getDecisionReason(), pushLanguageService.text("无决策原因", "No decision reason")), 50))
                .collect(Collectors.toList());
    }

    private boolean sourceContains(AStockPushHealthCheckResult result, String marker) {
        return result != null
                && StringUtils.containsIgnoreCase(StringUtils.defaultString(result.getSnapshotSource()), marker);
    }

    private String snapshotHealthLabel(MarketSnapshotHealth snapshotHealth) {
        return snapshotHealth != null ? pushLanguageService.snapshotHealthLabel(snapshotHealth) : "--";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
