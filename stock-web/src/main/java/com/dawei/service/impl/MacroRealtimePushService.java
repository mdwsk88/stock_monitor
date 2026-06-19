package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.service.AStockPushLogService;
import com.dawei.service.MarketStateService;
import com.dawei.service.ThemeAutoPoolService;
import com.dawei.utils.MarketStateSafety;
import com.dawei.utils.AStockEngagementMarkdown;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final LocalTime SESSION_START = LocalTime.of(9, 30);
    private static final LocalTime SESSION_END = LocalTime.of(15, 0);
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
    private final AStockRssMapper aStockRssMapper;
    private final ThemeAutoPoolService themeAutoPoolService;
    private final AStockPushLogService aStockPushLogService;
    private final MarketStateService marketStateService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;

    public MacroRealtimePushService(MacroThemeEventMapper macroThemeEventMapper,
                                    MacroThemeStockRelMapper macroThemeStockRelMapper,
                                    AStockRssMapper aStockRssMapper,
                                    ThemeAutoPoolService themeAutoPoolService,
                                    AStockPushLogService aStockPushLogService,
                                    MarketStateService marketStateService,
                                    WeComApi weComApi,
                                    StockFilterConfig filterConfig) {
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
        this.aStockRssMapper = aStockRssMapper;
        this.themeAutoPoolService = themeAutoPoolService;
        this.aStockPushLogService = aStockPushLogService;
        this.marketStateService = marketStateService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
    }

    public boolean handlePersistedEvent(MacroThemeEvent event) {
        return pushIfNeeded(event, marketStateService.getLatestSnapshot(), LocalDateTime.now(), false);
    }

    public boolean handlePersistedEventManually(MacroThemeEvent event) {
        return pushIfNeeded(event, marketStateService.getLatestSnapshot(), LocalDateTime.now(), true);
    }

    public MacroRealtimePushScanResult scanAndPushRecentEvents() {
        return scanAndPushRecentEvents(false);
    }

    public MacroRealtimePushScanResult scanAndPushRecentEventsManually() {
        return scanAndPushRecentEvents(true);
    }

    private MacroRealtimePushScanResult scanAndPushRecentEvents(boolean manualTrigger) {
        LocalDateTime now = LocalDateTime.now();
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

        MacroRealtimePushScanResult result = new MacroRealtimePushScanResult();
        result.setScannedCount(events.size());
        for (MacroThemeEvent event : events) {
            if (pushIfNeeded(event, snapshot, now, manualTrigger)) {
                result.setPushedCount(result.getPushedCount() + 1);
                result.getPushedTitles().add(StringUtils.defaultIfBlank(event.getTitle(), event.getThemeName()));
            } else {
                result.setSkippedCount(result.getSkippedCount() + 1);
            }
        }
        return result;
    }

    private boolean pushIfNeeded(MacroThemeEvent event,
                                 MarketSnapshot snapshot,
                                 LocalDateTime now,
                                 boolean manualTrigger) {
        if (!manualTrigger && !isWithinTradingSession(now)) {
            log.debug("宏观实时推送当前不在盘中窗口，跳过。theme={}, title={}",
                    event != null ? event.getThemeName() : "unknown",
                    event != null ? event.getTitle() : "");
            return false;
        }

        MacroPushDecision decision = classify(event, snapshot);
        if (!decision.shouldPush()) {
            return false;
        }

        List<ResonanceStockCandidate> candidates = loadResonanceStocks(event, now);
        String pushKey = buildPushKey(event);
        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getMacroRealtimePushCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(pushKey, decision.pushType(), cooldownStart)) {
            log.info("宏观实时推送命中冷却期，theme={}, title={}", event.getThemeName(), event.getTitle());
            return false;
        }

        try {
            weComApi.sendMarkdownMessage(buildMarkdown(event, snapshot, decision, candidates, now), WeComApi.MarketType.A);
            aStockPushLogService.recordPush(buildPushLog(pushKey, event, decision, candidates, now));
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
                        "盘中黑天鹅风险触发，事件类型=" + StringUtils.defaultString(event.getEventType(), "未知"));
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
                    "盘中风口共振触发，主题=" + StringUtils.defaultString(event.getThemeName(), "未知"));
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
        return RISK_EVENT_TYPES.contains(StringUtils.defaultString(event.getEventType()))
                || containsAny(event, RISK_KEYWORDS);
    }

    private boolean isOpportunityCandidate(MacroThemeEvent event) {
        return OPPORTUNITY_EVENT_TYPES.contains(StringUtils.defaultString(event.getEventType()))
                || containsAny(event, OPPORTUNITY_KEYWORDS);
    }

    private boolean containsAny(MacroThemeEvent event, Set<String> keywords) {
        String text = StringUtils.defaultString(event.getTitle()) + " "
                + StringUtils.defaultString(event.getSummary()) + " "
                + StringUtils.defaultString(event.getThemeName()) + " "
                + StringUtils.defaultString(event.getEventType());
        return keywords.stream().anyMatch(text::contains);
    }

    private List<ResonanceStockCandidate> loadResonanceStocks(MacroThemeEvent event, LocalDateTime now) {
        if (event == null || StringUtils.isBlank(event.getThemeName())) {
            return List.of();
        }
        int displayLimit = Math.max(1, filterConfig.getMacroRealtimeResonanceStockLimit());
        int preloadLimit = Math.max(displayLimit * 3, displayLimit);
        Map<String, ResonanceStockCandidate> candidateByCode = new LinkedHashMap<>();

        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .eq("theme_event_id", event.getId())
                .orderByDesc("confidence")
                .orderByAsc("stock_code")
                .last("LIMIT " + preloadLimit));
        for (MacroThemeStockRel relation : relations) {
            ResonanceStockCandidate candidate = candidateByCode.computeIfAbsent(
                    relation.getStockCode(),
                    unused -> new ResonanceStockCandidate(relation.getStockCode(), relation.getStockName())
            );
            candidate.absorbRelation(relation);
        }

        List<ThemeAutoPoolCandidate> autoPoolCandidates = themeAutoPoolService.list(event.getThemeName(), 1);
        for (ThemeAutoPoolCandidate autoPoolCandidate : autoPoolCandidates.stream().limit(preloadLimit).toList()) {
            if (StringUtils.isBlank(autoPoolCandidate.getStockCode())) {
                continue;
            }
            ResonanceStockCandidate candidate = candidateByCode.computeIfAbsent(
                    autoPoolCandidate.getStockCode(),
                    unused -> new ResonanceStockCandidate(autoPoolCandidate.getStockCode(), autoPoolCandidate.getStockName())
            );
            candidate.absorbAutoPool(autoPoolCandidate);
        }

        if (candidateByCode.isEmpty()) {
            return List.of();
        }

        LocalDateTime lookbackStart = now.minusDays(filterConfig.getMacroRealtimeResonanceLookbackDays());
        List<AStockRss> notices = aStockRssMapper.selectList(new QueryWrapper<AStockRss>()
                .in("stock_code", candidateByCode.keySet())
                .ge("pub_date", lookbackStart)
                .ge("signal_score", filterConfig.getMacroRealtimeResonanceNoticeSignalThreshold())
                .orderByDesc("signal_score")
                .orderByDesc("pub_date"));
        for (AStockRss notice : notices) {
            ResonanceStockCandidate candidate = candidateByCode.get(notice.getStockCode());
            if (candidate != null) {
                candidate.absorbNotice(notice);
            }
        }

        return candidateByCode.values().stream()
                .sorted(Comparator
                        .comparingInt(ResonanceStockCandidate::rankingScore).reversed()
                        .thenComparing(ResonanceStockCandidate::latestReferenceTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ResonanceStockCandidate::stockCode, Comparator.nullsLast(String::compareTo)))
                .limit(displayLimit)
                .toList();
    }

    private String buildMarkdown(MacroThemeEvent event,
                                 MarketSnapshot snapshot,
                                 MacroPushDecision decision,
                                 List<ResonanceStockCandidate> candidates,
                                 LocalDateTime now) {
        boolean risk = decision.pushType() == AStockPushType.MACRO_REALTIME_RISK;
        String title = risk ? "# 🚨 盘中黑天鹅避险" : "# ⚡ 盘中风口瞬时共振";
        String rating = risk ? resolveRiskRating(event) : resolveOpportunityRating(event);
        String candidateHeader = risk ? "⚠️ 风险敞口股票池" : "💡 AI 记忆库匹配（潜伏标的）";
        String candidateEmpty = risk
                ? "当前未命中明确风险敞口股票池，先盯住板块高贝塔后排与出口链方向。"
                : "当前未命中自动候选池或近 30 天高分公告，先盯主线龙头与板块扩散强度。";

        StringBuilder builder = new StringBuilder()
                .append(title).append("\n\n")
                .append("> **突发事件**：")
                .append(StringUtils.defaultIfBlank(event.getTitle(), "无标题"))
                .append("（").append(formatTime(event.getPubDate())).append("）\n")
                .append("> **主题/事件**：")
                .append(StringUtils.defaultIfBlank(event.getThemeName(), "未知主题"))
                .append(" | ")
                .append(StringUtils.defaultIfBlank(event.getEventType(), "未知事件"))
                .append(" | ")
                .append(StringUtils.defaultIfBlank(event.getSignalSide(), "未知方向"))
                .append(" | ")
                .append(safeInt(event.getSignalScore())).append(" 分\n")
                .append("> **风口评级**：<font color=\"")
                .append(risk ? "warning" : "info")
                .append("\">").append(rating).append("</font>\n")
                .append("> **市场状态**：").append(resolveStateLabel(snapshot)).append("\n");

        if (StringUtils.isNotBlank(event.getSummary())) {
            builder.append("> **事件解读**：").append(truncate(event.getSummary(), 110)).append("\n");
        }
        builder.append("> **盘中动作**：").append(resolveAction(risk, effectiveMarketState(snapshot))).append("\n\n")
                .append("## ").append(candidateHeader).append("\n");

        if (candidates.isEmpty()) {
            builder.append("<font color=\"comment\">").append(candidateEmpty).append("</font>\n\n");
        } else {
            for (ResonanceStockCandidate candidate : candidates) {
                builder.append("- **")
                        .append(candidate.stockNameOrCode())
                        .append(" (").append(candidate.stockCode()).append(")**：")
                        .append(candidate.describe())
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("<font color=\"comment\">触发时间：")
                .append(now.format(TIME_FORMATTER))
                .append("。仅供盘中研究与信息跟踪，不构成任何投资建议。</font>");
        return AStockEngagementMarkdown.appendRealtimeTail(builder.toString(), event.getThemeName());
    }

    private String resolveOpportunityRating(MacroThemeEvent event) {
        int score = safeInt(event.getSignalScore());
        int importance = safeInt(event.getImportanceLevel());
        if (score >= 100 || importance >= 5) {
            return "⭐⭐⭐⭐⭐ 极度利好";
        }
        if (score >= 92 || importance >= 4) {
            return "⭐⭐⭐⭐ 高置信利好";
        }
        return "⭐⭐⭐ 重点跟踪";
    }

    private String resolveRiskRating(MacroThemeEvent event) {
        int score = safeInt(event.getSignalScore());
        int importance = safeInt(event.getImportanceLevel());
        if (score >= 96 || importance >= 5) {
            return "💀💀💀💀💀 极强利空";
        }
        if (score >= 88 || importance >= 4) {
            return "💀💀💀💀 严重利空";
        }
        return "💀💀💀 风险升温";
    }

    private String resolveAction(boolean risk, MarketState state) {
        if (risk) {
            return switch (state) {
                case DEFENSIVE -> "优先规避受冲击板块和高弹性后排，别在脆弱盘面里硬接飞刀。";
                case RISK_ON, OVERHEAT -> "重点检查高位拥挤方向和外部敞口标的，防止盘中情绪回撤被放大。";
                default -> "先看板块是否同步走弱，再决定是否把风险上升为系统性撤退信号。";
            };
        }
        return switch (state) {
            case DEFENSIVE -> "防守盘里只看真正的主线核心，不把边缘跟风误判成主升。";
            case OVERHEAT -> "主线虽强也要提防后排兑现，只盯辨识度最高的带队股。";
            case RISK_ON -> "优先看能带板块扩散的龙头和高共振标的，别被杂讯分散注意力。";
            default -> "先确认板块承接和资金扩散，再决定这条催化是不是盘中真风口。";
        };
    }

    private String resolveStateLabel(MarketSnapshot snapshot) {
        MarketState state = effectiveMarketState(snapshot);
        String label = state.getLabel();
        if (snapshot != null
                && snapshot.getSnapshotHealth() != null
                && snapshot.getSnapshotHealth() != MarketSnapshotHealth.LIVE) {
            label += "（快照" + snapshot.getSnapshotHealth().getLabel() + "）";
        }
        return label;
    }

    private boolean isWithinTradingSession(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(SESSION_START) && !time.isAfter(SESSION_END);
    }

    private MarketState effectiveMarketState(MarketSnapshot snapshot) {
        return MarketStateSafety.normalize(snapshot, filterConfig.getMarketBreadthSampleWarnThreshold());
    }

    private AStockPushLog buildPushLog(String pushKey,
                                       MacroThemeEvent event,
                                       MacroPushDecision decision,
                                       List<ResonanceStockCandidate> candidates,
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
        pushLog.setDecisionReason(decision.reason() + "，命中候选股 " + candidates.size() + " 只");
        pushLog.setPushedAt(now);
        pushLog.setCreateTime(now);
        return pushLog;
    }

    private String buildPushKey(MacroThemeEvent event) {
        String rawKey = StringUtils.defaultIfBlank(event.getClusterKey(),
                StringUtils.defaultIfBlank(event.getNewsKey(), event.getId()));
        String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
        return "macro-realtime|" + digest;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "未知" : time.format(TIME_FORMATTER);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }

    private record MacroPushDecision(AStockPushType pushType, boolean shouldPush, String reason) {

        private static MacroPushDecision send(AStockPushType pushType, String reason) {
            return new MacroPushDecision(pushType, true, reason);
        }

        private static MacroPushDecision skip(String reason) {
            return new MacroPushDecision(AStockPushType.REPORT_ONLY, false, reason);
        }
    }

    private static final class ResonanceStockCandidate {
        private final String stockCode;
        private String stockName;
        private Integer relationConfidence;
        private String relationReason;
        private Integer autoPoolScore;
        private Integer autoHitCount;
        private String autoPoolReason;
        private LocalDateTime latestThemeHitTime;
        private AStockRss bestNotice;

        private ResonanceStockCandidate(String stockCode, String stockName) {
            this.stockCode = stockCode;
            this.stockName = stockName;
        }

        private void absorbRelation(MacroThemeStockRel relation) {
            stockName = prefer(stockName, relation.getStockName());
            if (relationConfidence == null || safeInt(relation.getConfidence()) > relationConfidence) {
                relationConfidence = safeInt(relation.getConfidence());
                relationReason = relation.getReason();
            }
            latestThemeHitTime = max(latestThemeHitTime, relation.getCreateTime());
        }

        private void absorbAutoPool(ThemeAutoPoolCandidate candidate) {
            stockName = prefer(stockName, candidate.getStockName());
            if (autoPoolScore == null || safeInt(candidate.getCandidateScore()) > autoPoolScore) {
                autoPoolScore = safeInt(candidate.getCandidateScore());
                autoPoolReason = candidate.getReason();
            }
            autoHitCount = Math.max(safeInt(autoHitCount), safeInt(candidate.getHitCount()));
            latestThemeHitTime = max(latestThemeHitTime, candidate.getLatestPubDate());
        }

        private void absorbNotice(AStockRss notice) {
            if (bestNotice == null
                    || safeInt(notice.getSignalScore()) > safeInt(bestNotice.getSignalScore())
                    || (safeInt(notice.getSignalScore()) == safeInt(bestNotice.getSignalScore())
                    && notice.getPubDate() != null
                    && (bestNotice.getPubDate() == null || notice.getPubDate().isAfter(bestNotice.getPubDate())))) {
                bestNotice = notice;
            }
        }

        private int rankingScore() {
            return Math.max(Math.max(safeInt(relationConfidence), safeInt(autoPoolScore)),
                    bestNotice != null ? safeInt(bestNotice.getSignalScore()) : 0);
        }

        private LocalDateTime latestReferenceTime() {
            if (bestNotice != null) {
                return max(latestThemeHitTime, bestNotice.getPubDate());
            }
            return latestThemeHitTime;
        }

        private String stockCode() {
            return stockCode;
        }

        private String stockNameOrCode() {
            return StringUtils.defaultIfBlank(stockName, stockCode);
        }

        private String describe() {
            List<String> parts = new ArrayList<>();
            if (bestNotice != null && bestNotice.getSignalScore() != null) {
                parts.add("历史信号 " + bestNotice.getSignalScore() + " 分");
            }
            if (relationConfidence != null && relationConfidence > 0) {
                parts.add("主题映射置信度 " + relationConfidence);
            }
            if (autoPoolScore != null && autoPoolScore > 0) {
                String suffix = autoHitCount != null && autoHitCount > 0 ? "，累计命中 " + autoHitCount + " 次" : "";
                parts.add("自动候选池 " + autoPoolScore + " 分" + suffix);
            }
            String lead = String.join("；", parts);

            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(lead)) {
                builder.append(lead).append("。");
            }
            String reason = StringUtils.defaultIfBlank(relationReason, autoPoolReason);
            if (StringUtils.isNotBlank(reason)) {
                builder.append("匹配依据：").append(trimReason(reason)).append("。");
            }
            if (bestNotice != null && StringUtils.isNotBlank(bestNotice.getTitle())) {
                builder.append("最近高分公告：").append(trimReason(bestNotice.getTitle())).append("。");
            }
            if (builder.length() == 0) {
                builder.append("主题命中成功，建议结合板块承接继续跟踪。");
            }
            return builder.toString();
        }

        private static String prefer(String current, String incoming) {
            return StringUtils.isNotBlank(current) ? current : incoming;
        }

        private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.isAfter(right) ? left : right;
        }

        private static int safeInt(Integer value) {
            return value == null ? 0 : value;
        }

        private static String trimReason(String value) {
            if (StringUtils.isBlank(value)) {
                return "";
            }
            String normalized = value.replaceAll("\\s+", " ").trim();
            return normalized.length() <= 42 ? normalized : normalized.substring(0, 42) + "...";
        }
    }
}
