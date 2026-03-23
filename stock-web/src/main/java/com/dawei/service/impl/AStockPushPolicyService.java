package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.utils.AStockMarketClock;
import com.dawei.utils.MarketStateSafety;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A股实时推送策略服务
 */
@Service
public class AStockPushPolicyService {

    private static final Set<String> OPPORTUNITY_EVENT_TYPES = Set.of(
            "重大合同", "并购重组", "业绩兑现", "产品获批", "回购增持"
    );
    private static final Set<String> RISK_EVENT_TYPES = Set.of(
            "退市风险", "监管处罚", "重整风险", "司法处置", "诉讼仲裁", "业绩承压"
    );
    private static final Set<String> STRICT_SILENT_EVENT_TYPES = Set.of(
            "常规事项", "资本动作", "交易风险"
    );
    private static final List<String> STRICT_SILENT_TITLE_KEYWORDS = List.of(
            "董事会决议", "监事会决议", "股东大会决议", "临时股东大会决议",
            "会议通知", "召开会议", "进展公告", "日常经营", "换发营业执照",
            "营业执照", "异常波动", "股票交易异常波动", "回复公告",
            "募集资金", "非经营性资金占用", "制度修订", "章程修订"
    );

    private final StockFilterConfig filterConfig;

    public AStockPushPolicyService(StockFilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public AStockPushDecision classify(AStockRss notice, LocalDateTime now) {
        return classify(notice, now, MarketSnapshot.neutral(now, "LEGACY"));
    }

    public AStockPushDecision classify(AStockRss notice, LocalDateTime now, MarketSnapshot marketSnapshot) {
        if (notice == null || notice.getTitle() == null || notice.getTitle().isBlank()) {
            return new AStockPushDecision(AStockPushType.SILENT, "公告为空", false, false);
        }

        MarketState marketState = effectiveMarketState(marketSnapshot);
        int signalScore = safeScore(notice.getSignalScore());
        boolean critical = signalScore >= filterConfig.getARealtimeCriticalThreshold();
        String eventType = normalize(notice.getEventType());
        String title = normalize(notice.getTitle());

        if (signalScore < filterConfig.getARankingSignalThreshold()) {
            return new AStockPushDecision(AStockPushType.SILENT, "评分未达到报告线", critical, false);
        }

        if (shouldSilenceImmediately(title, eventType, critical)) {
            return new AStockPushDecision(AStockPushType.SILENT, "命中实时静默规则", critical, false);
        }

        LocalDateTime eventTime = resolveEventTime(notice, now);
        boolean withinTradingSession = isTradingSession(eventTime);
        if (!withinTradingSession) {
            return new AStockPushDecision(AStockPushType.REPORT_ONLY, withStateSuffix("非盘中交易窗口", marketSnapshot, marketState), critical, false);
        }

        if (marketState == MarketState.DEFENSIVE && "利多".equals(notice.getSignalSide()) && !critical) {
            return new AStockPushDecision(AStockPushType.REPORT_ONLY, withStateSuffix("防守态抑制盘中追涨", marketSnapshot, marketState), critical, true);
        }

        if (isOpportunityRealtime(notice, signalScore, marketState)) {
            return new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, withStateSuffix("高分利多白名单事件", marketSnapshot, marketState), critical, true);
        }

        if (isRiskRealtime(notice, signalScore, marketState)) {
            return new AStockPushDecision(AStockPushType.REALTIME_RISK, withStateSuffix("高分利空白名单事件", marketSnapshot, marketState), critical, true);
        }

        return new AStockPushDecision(AStockPushType.REPORT_ONLY, withStateSuffix("仅保留至晨报/复盘", marketSnapshot, marketState), critical, withinTradingSession);
    }

    public AStockPushDecision refineRealtimeDecision(AStockRss notice,
                                                     AStockPushDecision initialDecision,
                                                     MarketSnapshot marketSnapshot,
                                                     AStockRealtimeContext realtimeContext,
                                                     AReportOpportunityInsight opportunityInsight) {
        if (initialDecision == null || !initialDecision.shouldSendRealtime()) {
            return initialDecision;
        }
        if (initialDecision.getPushType() != AStockPushType.REALTIME_OPPORTUNITY) {
            return initialDecision;
        }

        MarketState marketState = effectiveMarketState(marketSnapshot);
        boolean critical = initialDecision.isCritical();
        String positionLabel = opportunityInsight != null ? opportunityInsight.getPositionLabel() : "";

        if (marketState == MarketState.OVERHEAT && !"领军核心".equals(positionLabel) && !critical) {
            return new AStockPushDecision(
                    AStockPushType.REPORT_ONLY,
                    withStateSuffix("高潮态仅放行领军核心，后排机会降级为报告观察", marketSnapshot, marketState),
                    critical,
                    initialDecision.isWithinTradingSession()
            );
        }

        if (marketState == MarketState.RISK_ON
                && "观察名单".equals(positionLabel)
                && (realtimeContext == null || !realtimeContext.hasResonance())
                && !critical) {
            return new AStockPushDecision(
                    AStockPushType.REPORT_ONLY,
                    withStateSuffix("进攻态下边际催化需等待主线确认", marketSnapshot, marketState),
                    critical,
                    initialDecision.isWithinTradingSession()
            );
        }

        if (opportunityInsight == null || StringUtils.isBlank(positionLabel)) {
            return initialDecision;
        }
        return new AStockPushDecision(
                initialDecision.getPushType(),
                initialDecision.getReason() + " | 身位=" + positionLabel,
                critical,
                initialDecision.isWithinTradingSession()
        );
    }

    boolean isTradingSession(LocalDateTime now) {
        return AStockMarketClock.isTradingSession(now);
    }

    private boolean isOpportunityRealtime(AStockRss notice, int signalScore, MarketState marketState) {
        if (!"利多".equals(notice.getSignalSide())) {
            return false;
        }
        if (marketState == MarketState.DEFENSIVE) {
            return false;
        }
        if (!OPPORTUNITY_EVENT_TYPES.contains(normalize(notice.getEventType()))) {
            return false;
        }
        int threshold = resolveOpportunityThreshold(marketState);
        if ("回购增持".equals(normalize(notice.getEventType()))) {
            return signalScore >= Math.max(threshold, 90);
        }
        return signalScore >= threshold;
    }

    private boolean isRiskRealtime(AStockRss notice, int signalScore, MarketState marketState) {
        return "利空".equals(notice.getSignalSide())
                && RISK_EVENT_TYPES.contains(normalize(notice.getEventType()))
                && signalScore >= resolveRiskThreshold(marketState);
    }

    private boolean shouldSilenceImmediately(String title, String eventType, boolean critical) {
        if (STRICT_SILENT_EVENT_TYPES.contains(eventType)) {
            return !critical;
        }
        if (critical && ("退市风险".equals(eventType) || "监管处罚".equals(eventType))) {
            return false;
        }
        return STRICT_SILENT_TITLE_KEYWORDS.stream().anyMatch(title::contains);
    }

    private int safeScore(Integer signalScore) {
        return signalScore == null ? 0 : signalScore;
    }

    private int resolveOpportunityThreshold(MarketState marketState) {
        return switch (marketState) {
            case RISK_ON -> filterConfig.getARealtimeOpportunityThresholdRiskOn();
            case OVERHEAT -> filterConfig.getARealtimeOpportunityThresholdOverheat();
            default -> filterConfig.getARealtimeOpportunityThreshold();
        };
    }

    private int resolveRiskThreshold(MarketState marketState) {
        return marketState == MarketState.DEFENSIVE
                ? filterConfig.getARealtimeRiskThresholdDefensive()
                : filterConfig.getARealtimeRiskThreshold();
    }

    private LocalDateTime resolveEventTime(AStockRss notice, LocalDateTime now) {
        if (notice != null && notice.getPubDate() != null) {
            return notice.getPubDate();
        }
        return now;
    }

    private MarketState effectiveMarketState(MarketSnapshot marketSnapshot) {
        return MarketStateSafety.normalize(marketSnapshot, filterConfig.getMarketBreadthSampleWarnThreshold());
    }

    private String withStateSuffix(String reason, MarketSnapshot marketSnapshot, MarketState marketState) {
        StringBuilder builder = new StringBuilder(reason)
                .append(" | 市场状态=")
                .append(marketState.getLabel());
        if (marketSnapshot != null
                && marketSnapshot.getSnapshotHealth() != null
                && marketSnapshot.getSnapshotHealth() != MarketSnapshotHealth.LIVE) {
            builder.append(" | 快照=").append(marketSnapshot.getSnapshotHealth().getLabel());
        }
        if (MarketStateSafety.hasNoBreadth(marketSnapshot)) {
            builder.append(" | 宽度=缺失");
        } else if (MarketStateSafety.hasLowConfidenceSampleBreadth(marketSnapshot, filterConfig.getMarketBreadthSampleWarnThreshold())) {
            builder.append(" | 宽度=低置信样本");
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
