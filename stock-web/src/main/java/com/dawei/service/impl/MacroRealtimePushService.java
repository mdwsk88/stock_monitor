package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.MarketAlertFamily;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.service.AStockPushLogService;
import com.dawei.service.MarketStateService;
import com.dawei.utils.AStockMarketClock;
import com.dawei.utils.MarketStateSafety;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 宏观快讯实时推送服务。
 */
@Slf4j
@Service
public class MacroRealtimePushService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> RISK_EVENT_TYPES = Set.of(
            "流动性收紧", "监管收紧", "贸易摩擦", "地缘与商品冲击", "价格承压"
    );
    private static final Set<String> OPPORTUNITY_EVENT_TYPES = Set.of(
            "政策发布", "流动性宽松", "政策扶持", "价格催化", "景气催化"
    );
    private static final Set<String> RISK_KEYWORDS = Set.of(
            "关税", "加征关税", "制裁", "净回笼", "收紧", "严查", "监管", "地缘", "冲突", "风险"
    );
    private static final Set<String> OPPORTUNITY_KEYWORDS = Set.of(
            "支持", "降准", "降息", "净投放", "补贴", "方案", "规划", "提价", "景气", "需求回暖"
    );

    private final MacroThemeEventMapper macroThemeEventMapper;
    private final MacroThemeStockRelMapper macroThemeStockRelMapper;
    private final AStockPushLogService aStockPushLogService;
    private final MarketStateService marketStateService;
    private final MarketAlertCoordinatorService marketAlertCoordinatorService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;
    private final PushLanguageService pushLanguageService;

    public MacroRealtimePushService(MacroThemeEventMapper macroThemeEventMapper,
                                    MacroThemeStockRelMapper macroThemeStockRelMapper,
                                    AStockPushLogService aStockPushLogService,
                                    MarketStateService marketStateService,
                                    MarketAlertCoordinatorService marketAlertCoordinatorService,
                                    WeComApi weComApi,
                                    StockFilterConfig filterConfig) {
        this(macroThemeEventMapper, macroThemeStockRelMapper, aStockPushLogService, marketStateService,
                marketAlertCoordinatorService, weComApi, filterConfig, new PushLanguageService());
    }

    @Autowired
    public MacroRealtimePushService(MacroThemeEventMapper macroThemeEventMapper,
                                    MacroThemeStockRelMapper macroThemeStockRelMapper,
                                    AStockPushLogService aStockPushLogService,
                                    MarketStateService marketStateService,
                                    MarketAlertCoordinatorService marketAlertCoordinatorService,
                                    WeComApi weComApi,
                                    StockFilterConfig filterConfig,
                                    PushLanguageService pushLanguageService) {
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
        this.aStockPushLogService = aStockPushLogService;
        this.marketStateService = marketStateService;
        this.marketAlertCoordinatorService = marketAlertCoordinatorService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
        this.pushLanguageService = pushLanguageService;
    }

    public boolean handlePersistedEvent(MacroThemeEvent event) {
        return handlePersistedEvent(event, LocalDateTime.now());
    }

    boolean handlePersistedEvent(MacroThemeEvent event, LocalDateTime now) {
        return pushIfNeeded(event, marketStateService.getLatestSnapshot(), now);
    }

    public MacroRealtimePushScanResult scanAndPushRecentEvents() {
        return scanAndPushRecentEvents(LocalDateTime.now());
    }

    MacroRealtimePushScanResult scanAndPushRecentEvents(LocalDateTime now) {
        MacroRealtimePushScanResult result = new MacroRealtimePushScanResult();
        if (!AStockMarketClock.isMarketAttentionWindow(now)) {
            return result;
        }
        LocalDateTime startTime = now.minusMinutes(filterConfig.getMacroRealtimeScanWindowMinutes());
        int minScore = Math.min(
                filterConfig.getMacroRealtimeRiskThresholdDefensive(),
                filterConfig.getMacroRealtimeOpportunityThresholdRiskOn()
        );
        List<MacroThemeEvent> events = macroThemeEventMapper.selectList(new QueryWrapper<MacroThemeEvent>()
                .ge("pub_date", startTime)
                .in("signal_side", List.of("利多", "利空"))
                .ge("signal_score", minScore)
                .orderByDesc("pub_date")
                .last("LIMIT 20"));
        MarketSnapshot snapshot = marketStateService.getLatestSnapshot();

        result.setScannedCount(events.size());
        for (MacroThemeEvent event : events) {
            if (pushIfNeeded(event, snapshot, now)) {
                result.setPushedCount(result.getPushedCount() + 1);
                result.getPushedTitles().add(StringUtils.defaultIfBlank(event.getTitle(), event.getThemeName()));
            } else {
                result.setSkippedCount(result.getSkippedCount() + 1);
            }
        }
        return result;
    }

    private boolean pushIfNeeded(MacroThemeEvent event, MarketSnapshot snapshot, LocalDateTime now) {
        if (!AStockMarketClock.isMarketAttentionWindow(now)) {
            log.debug("宏观实时推送跳过，原因=当前不在A股关注窗口，title={}",
                    event != null ? event.getTitle() : "unknown");
            return false;
        }
        MacroPushDecision decision = classify(event, snapshot);
        if (!decision.shouldPush()) {
            return false;
        }

        String pushKey = buildPushKey(event);
        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getMacroRealtimePushCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(pushKey, decision.pushType(), cooldownStart)) {
            log.info("宏观实时推送命中冷却期，theme={}, title={}", event.getThemeName(), event.getTitle());
            return false;
        }

        MarketAlertCoordinatorService.AlertDispatchDecision dispatchDecision =
                marketAlertCoordinatorService.evaluate(
                        MarketAlertFamily.fromPushType(decision.pushType()),
                        decision.pushType(),
                        pushKey,
                        snapshot,
                        safeInt(event.getSignalScore()),
                        now
                );
        if (!dispatchDecision.allowed()) {
            log.info("宏观实时推送被协调器抑制，theme={}, title={}, reason={}",
                    event.getThemeName(), event.getTitle(), dispatchDecision.reason());
            return false;
        }

        try {
            weComApi.sendMarkdownMessage(buildMarkdown(event, snapshot, decision, now), WeComApi.MarketType.A);
            aStockPushLogService.recordPush(buildPushLog(pushKey, event, decision, now));
            return true;
        } catch (Exception ex) {
            log.error("宏观实时推送失败，theme={}, title={}, reason={}",
                    event.getThemeName(), event.getTitle(), ex.getMessage(), ex);
            return false;
        }
    }

    private MacroPushDecision classify(MacroThemeEvent event, MarketSnapshot snapshot) {
        if (event == null || StringUtils.isBlank(event.getSignalSide())) {
            return MacroPushDecision.skip("无效宏观事件");
        }
        int score = safeInt(event.getSignalScore());
        MarketState state = effectiveMarketState(snapshot);

        if ("利空".equals(event.getSignalSide())) {
            if (!isRiskCandidate(event)) {
                return MacroPushDecision.skip("未命中宏观风险白名单");
            }
            int threshold = state == MarketState.DEFENSIVE
                    ? filterConfig.getMacroRealtimeRiskThresholdDefensive()
                    : filterConfig.getMacroRealtimeRiskThreshold();
            if (score >= threshold) {
                return MacroPushDecision.send(AStockPushType.MACRO_REALTIME_RISK,
                        "宏观风险直推，事件类型=" + StringUtils.defaultString(event.getEventType(), "未知"));
            }
            return MacroPushDecision.skip("宏观风险分数不足");
        }

        if (!"利多".equals(event.getSignalSide())) {
            return MacroPushDecision.skip("中性宏观事件不做实时推送");
        }
        if (!isOpportunityCandidate(event)) {
            return MacroPushDecision.skip("未命中宏观机会白名单");
        }
        int threshold = resolveOpportunityThreshold(state);
        if (score >= threshold) {
            return MacroPushDecision.send(AStockPushType.MACRO_REALTIME_OPPORTUNITY,
                    "宏观机会直推，主题=" + StringUtils.defaultString(event.getThemeName(), "未知"));
        }
        return MacroPushDecision.skip("宏观机会分数不足");
    }

    private int resolveOpportunityThreshold(MarketState state) {
        return switch (state) {
            case DEFENSIVE -> Math.max(filterConfig.getMacroRealtimeOpportunityThreshold(), 108);
            case RISK_ON -> filterConfig.getMacroRealtimeOpportunityThresholdRiskOn();
            case OVERHEAT -> filterConfig.getMacroRealtimeOpportunityThresholdOverheat();
            default -> filterConfig.getMacroRealtimeOpportunityThreshold();
        };
    }

    private boolean isRiskCandidate(MacroThemeEvent event) {
        boolean typeMatched = RISK_EVENT_TYPES.contains(StringUtils.defaultString(event.getEventType()));
        boolean textMatched = containsAny(event, RISK_KEYWORDS);
        return textMatched && (typeMatched || hasStrongRiskSource(event));
    }

    private boolean isOpportunityCandidate(MacroThemeEvent event) {
        boolean typeMatched = OPPORTUNITY_EVENT_TYPES.contains(StringUtils.defaultString(event.getEventType()));
        boolean textMatched = containsAny(event, OPPORTUNITY_KEYWORDS);
        return textMatched && (typeMatched || hasStrongOpportunitySource(event));
    }

    private boolean containsAny(MacroThemeEvent event, Set<String> keywords) {
        String text = StringUtils.defaultString(event.getTitle()) + " "
                + StringUtils.defaultString(event.getSummary()) + " "
                + StringUtils.defaultString(event.getThemeName()) + " "
                + StringUtils.defaultString(event.getEventType());
        return keywords.stream().anyMatch(text::contains);
    }

    private boolean hasStrongRiskSource(MacroThemeEvent event) {
        String sourceName = StringUtils.defaultString(event.getSourceName());
        return sourceName.contains("证监")
                || sourceName.contains("国务院")
                || sourceName.contains("央行")
                || sourceName.contains("海关")
                || sourceName.contains("商务部")
                || sourceName.contains("发改委");
    }

    private boolean hasStrongOpportunitySource(MacroThemeEvent event) {
        String sourceName = StringUtils.defaultString(event.getSourceName());
        return sourceName.contains("国务院")
                || sourceName.contains("央行")
                || sourceName.contains("发改委")
                || sourceName.contains("工信")
                || sourceName.contains("财政")
                || sourceName.contains("中国政府网");
    }

    private String buildMarkdown(MacroThemeEvent event,
                                 MarketSnapshot snapshot,
                                 MacroPushDecision decision,
                                 LocalDateTime now) {
        boolean risk = decision.pushType() == AStockPushType.MACRO_REALTIME_RISK;
        String title = risk
                ? pushLanguageService.text("# 🚨 宏观系统性风险预警", "# 🚨 Macro Systemic Risk Alert")
                : pushLanguageService.text("# 🔥 宏观主线催化预警", "# 🔥 Macro Theme Catalyst Alert");
        MarketState effectiveState = effectiveMarketState(snapshot);
        String stateLabel = pushLanguageService.marketStateLabel(effectiveState);
        if (snapshot != null && snapshot.getSnapshotHealth() != null
                && snapshot.getSnapshotHealth() != MarketSnapshotHealth.LIVE) {
            stateLabel += pushLanguageService.text("（快照" + snapshot.getSnapshotHealth().getLabel() + "）",
                    " (Snapshot " + pushLanguageService.snapshotHealthLabel(snapshot.getSnapshotHealth()) + ")");
        }
        String relatedStocks = loadRelatedStocks(event.getId());
        String action = risk
                ? pushLanguageService.text("优先检查受冲击板块和高弹性后排，避免在系统性利空下逆势加仓。", "Check the exposed sectors and high-beta laggards first, and avoid adding risk against a systemic bearish catalyst.")
                : pushLanguageService.text("优先关注主线核心与高辨识度龙头，不要把边缘跟风误判成主升。", "Focus on the core names inside the active theme and the clearest leaders; do not mistake weak followers for primary trend leaders.");
        String source = StringUtils.defaultIfBlank(event.getSourceName(), pushLanguageService.text("宏观快讯", "Macro Wire"));
        return title + "\n\n"
                + "> **" + pushLanguageService.text("市场状态", "Market Regime") + "**：<font color=\"" + (risk ? "warning" : "info") + "\">" + stateLabel + "</font>\n"
                + "> **" + pushLanguageService.text("主题", "Theme") + "**：" + StringUtils.defaultIfBlank(event.getThemeName(), pushLanguageService.text("未知主题", "Unknown Theme"))
                + " | **" + pushLanguageService.text("事件", "Event") + "**：" + pushLanguageService.eventType(StringUtils.defaultIfBlank(event.getEventType(), pushLanguageService.text("未知事件", "Unknown Event")))
                + " | **" + pushLanguageService.text("方向", "Bias") + "**：" + pushLanguageService.signalSideLabel(event.getSignalSide())
                + " | **" + pushLanguageService.text("分数", "Score") + "**：" + safeInt(event.getSignalScore()) + "\n"
                + "> **" + pushLanguageService.text("来源", "Source") + "**：" + source + " | **" + pushLanguageService.text("发布时间", "Published At") + "**：" + formatTime(event.getPubDate()) + "\n"
                + "> **" + pushLanguageService.text("标题", "Title") + "**：" + StringUtils.defaultIfBlank(event.getTitle(), pushLanguageService.text("无标题", "Untitled")) + "\n"
                + "> **" + pushLanguageService.text("摘要", "Summary") + "**：" + StringUtils.defaultIfBlank(event.getSummary(), pushLanguageService.text("无摘要", "No Summary")) + "\n"
                + "> **" + pushLanguageService.text("关联A股", "Related Stocks") + "**：" + relatedStocks + "\n"
                + "> **" + pushLanguageService.text("执行提示", "Action") + "**：" + action + "\n"
                + "> **" + pushLanguageService.text("触发时间", "Triggered At") + "**：" + now.format(TIME_FORMATTER) + "\n\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("宏观实时推送只放行高置信度政策、监管、流动性与外部风险事件，用于补足盘中系统性信号。",
                        "Macro realtime alerts only pass through high-confidence policy, regulation, liquidity, and external risk events, and are meant to complement intraday systemic signals.")
                + "</font>";
    }

    private MarketState effectiveMarketState(MarketSnapshot snapshot) {
        return MarketStateSafety.normalize(snapshot, filterConfig.getMarketBreadthSampleWarnThreshold());
    }

    private AStockPushLog buildPushLog(String pushKey,
                                       MacroThemeEvent event,
                                       MacroPushDecision decision,
                                       LocalDateTime now) {
        AStockPushLog pushLog = new AStockPushLog();
        pushLog.setId(UUID.randomUUID().toString().replace("-", ""));
        pushLog.setPushKey(pushKey);
        pushLog.setPushType(decision.pushType().name());
        pushLog.setSignalSide(event.getSignalSide());
        pushLog.setSignalScore(event.getSignalScore());
        pushLog.setEventType(event.getEventType());
        pushLog.setTitle(event.getTitle());
        pushLog.setMacroThemeName(event.getThemeName());
        pushLog.setDecisionReason(decision.reason());
        pushLog.setPushedAt(now);
        pushLog.setCreateTime(now);
        return pushLog;
    }

    private String loadRelatedStocks(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            return pushLanguageService.text("暂无显式映射", "No explicit mapping");
        }
        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .eq("theme_event_id", eventId)
                .orderByDesc("confidence")
                .last("LIMIT 3"));
        if (relations == null || relations.isEmpty()) {
            return pushLanguageService.text("暂无显式映射", "No explicit mapping");
        }
        return relations.stream()
                .map(rel -> rel.getStockName() + "(" + rel.getStockCode() + ")")
                .collect(Collectors.joining("、"));
    }

    private String buildPushKey(MacroThemeEvent event) {
        String rawKey = StringUtils.defaultIfBlank(event.getClusterKey(),
                StringUtils.defaultIfBlank(event.getNewsKey(), event.getId()));
        String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
        return "macro-realtime|" + digest;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? pushLanguageService.text("未知", "Unknown") : time.format(TIME_FORMATTER);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record MacroPushDecision(AStockPushType pushType, boolean shouldPush, String reason) {

        private static MacroPushDecision send(AStockPushType pushType, String reason) {
            return new MacroPushDecision(pushType, true, reason);
        }

        private static MacroPushDecision skip(String reason) {
            return new MacroPushDecision(AStockPushType.REPORT_ONLY, false, reason);
        }
    }
}
