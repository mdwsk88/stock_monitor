package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A股实时推送策略服务
 */
@Service
public class AStockPushPolicyService {

    private static final LocalTime MORNING_SESSION_START = LocalTime.of(9, 15);
    private static final LocalTime MORNING_SESSION_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_SESSION_START = LocalTime.of(12, 55);
    private static final LocalTime AFTERNOON_SESSION_END = LocalTime.of(15, 0);

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
        if (notice == null || notice.getTitle() == null || notice.getTitle().isBlank()) {
            return new AStockPushDecision(AStockPushType.SILENT, "公告为空", false, false);
        }

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

        boolean withinTradingSession = isTradingSession(now);
        if (!withinTradingSession) {
            return new AStockPushDecision(AStockPushType.REPORT_ONLY, "非盘中交易窗口", critical, false);
        }

        if (isOpportunityRealtime(notice, signalScore)) {
            return new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", critical, true);
        }

        if (isRiskRealtime(notice, signalScore)) {
            return new AStockPushDecision(AStockPushType.REALTIME_RISK, "高分利空白名单事件", critical, true);
        }

        return new AStockPushDecision(AStockPushType.REPORT_ONLY, "仅保留至晨报/复盘", critical, withinTradingSession);
    }

    boolean isTradingSession(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime current = now.toLocalTime();
        boolean morning = !current.isBefore(MORNING_SESSION_START) && !current.isAfter(MORNING_SESSION_END);
        boolean afternoon = !current.isBefore(AFTERNOON_SESSION_START) && !current.isAfter(AFTERNOON_SESSION_END);
        return morning || afternoon;
    }

    private boolean isOpportunityRealtime(AStockRss notice, int signalScore) {
        if (!"利多".equals(notice.getSignalSide())) {
            return false;
        }
        if (!OPPORTUNITY_EVENT_TYPES.contains(normalize(notice.getEventType()))) {
            return false;
        }
        if ("回购增持".equals(normalize(notice.getEventType()))) {
            return signalScore >= Math.max(filterConfig.getARealtimeOpportunityThreshold(), 90);
        }
        return signalScore >= filterConfig.getARealtimeOpportunityThreshold();
    }

    private boolean isRiskRealtime(AStockRss notice, int signalScore) {
        return "利空".equals(notice.getSignalSide())
                && RISK_EVENT_TYPES.contains(normalize(notice.getEventType()))
                && signalScore >= filterConfig.getARealtimeRiskThreshold();
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
