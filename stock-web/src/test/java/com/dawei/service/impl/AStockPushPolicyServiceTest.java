package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStockPushPolicyServiceTest {

    private AStockPushPolicyService pushPolicyService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setARankingSignalThreshold(60);
        filterConfig.setARealtimeOpportunityThreshold(85);
        filterConfig.setARealtimeOpportunityThresholdRiskOn(78);
        filterConfig.setARealtimeOpportunityThresholdOverheat(80);
        filterConfig.setARealtimeRiskThreshold(88);
        filterConfig.setARealtimeRiskThresholdDefensive(70);
        filterConfig.setARealtimeCriticalThreshold(92);
        filterConfig.setMarketBreadthSampleWarnThreshold(200);
        pushPolicyService = new AStockPushPolicyService(filterConfig);
    }

    @Test
    void classify_ShouldSilenceRoutineResolutionNotice() {
        AStockRss notice = buildNotice(
                "林洋能源:2026年第一次临时股东大会决议公告",
                "常规事项",
                "中性",
                78
        );

        AStockPushDecision decision = pushPolicyService.classify(notice, LocalDateTime.of(2026, 3, 18, 10, 5));

        assertEquals(AStockPushType.SILENT, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldKeepHighScoreOpportunityForReportOutsideTradingSession() {
        AStockRss notice = buildNotice(
                "平安银行:关于中标10亿元算力项目的公告",
                "重大合同",
                "利多",
                90
        );

        AStockPushDecision decision = pushPolicyService.classify(notice, LocalDateTime.of(2026, 3, 18, 20, 15));

        assertEquals(AStockPushType.REPORT_ONLY, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
        assertFalse(decision.isWithinTradingSession());
    }

    @Test
    void classify_ShouldEmitRealtimeOpportunityDuringTradingSession() {
        AStockRss notice = buildNotice(
                "航天彩虹:关于签订8亿元无人机海外订单的公告",
                "重大合同",
                "利多",
                89
        );

        AStockPushDecision decision = pushPolicyService.classify(notice, LocalDateTime.of(2026, 3, 18, 13, 26));

        assertEquals(AStockPushType.REALTIME_OPPORTUNITY, decision.getPushType());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldEmitRealtimeRiskDuringTradingSession() {
        AStockRss notice = buildNotice(
                "*ST熊猫:关于公司股票可能被终止上市的风险提示公告",
                "退市风险",
                "利空",
                91
        );

        AStockPushDecision decision = pushPolicyService.classify(notice, LocalDateTime.of(2026, 3, 18, 9, 45));

        assertEquals(AStockPushType.REALTIME_RISK, decision.getPushType());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldAllowCriticalRiskEvenWhenTitleContainsAbnormalVolatilityNoise() {
        AStockRss notice = buildNotice(
                "某公司:股票交易异常波动暨收到中国证监会立案告知书的公告",
                "监管处罚",
                "利空",
                94
        );

        AStockPushDecision decision = pushPolicyService.classify(notice, LocalDateTime.of(2026, 3, 18, 10, 18));

        assertEquals(AStockPushType.REALTIME_RISK, decision.getPushType());
        assertTrue(decision.isCritical());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldUseNoticePublishTimeInsteadOfProcessTime() {
        AStockRss notice = buildNotice(
                "某公司:关于收到立案告知书的公告",
                "监管处罚",
                "利空",
                90
        );
        notice.setPubDate(LocalDateTime.of(2026, 3, 18, 14, 58));

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 15, 5),
                snapshot(MarketState.NEUTRAL)
        );

        assertEquals(AStockPushType.REALTIME_RISK, decision.getPushType());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldSuppressBuyDuringDefensiveState() {
        AStockRss notice = buildNotice(
                "航天彩虹:关于签订8亿元无人机海外订单的公告",
                "重大合同",
                "利多",
                91
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.DEFENSIVE)
        );

        assertEquals(AStockPushType.REPORT_ONLY, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldLowerRiskThresholdDuringDefensiveState() {
        AStockRss notice = buildNotice(
                "某公司:关于累计诉讼仲裁事项的公告",
                "诉讼仲裁",
                "利空",
                72
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.DEFENSIVE)
        );

        assertEquals(AStockPushType.REALTIME_RISK, decision.getPushType());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldLowerOpportunityThresholdDuringRiskOnState() {
        AStockRss notice = buildNotice(
                "北路智控:关于重大合同中标的公告",
                "重大合同",
                "利多",
                79
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.RISK_ON)
        );

        assertEquals(AStockPushType.REALTIME_OPPORTUNITY, decision.getPushType());
        assertTrue(decision.shouldSendRealtime());
    }

    @Test
    void classify_ShouldFallbackToNeutralWhenBreadthUnavailable() {
        AStockRss notice = buildNotice(
                "北路智控:关于重大合同中标的公告",
                "重大合同",
                "利多",
                79
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.RISK_ON, MarketSnapshotHealth.LIVE, "TENCENT_QUOTE+NO_BREADTH", 0)
        );

        assertEquals(AStockPushType.REPORT_ONLY, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
        assertTrue(decision.getReason().contains("宽度=缺失"));
    }

    @Test
    void classify_ShouldFallbackToDefensiveWhenSnapshotDisconnected() {
        AStockRss notice = buildNotice(
                "航天彩虹:关于签订8亿元无人机海外订单的公告",
                "重大合同",
                "利多",
                91
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.RISK_ON, MarketSnapshotHealth.DISCONNECTED, "TENCENT_QUOTE+NO_BREADTH", 0)
        );

        assertEquals(AStockPushType.REPORT_ONLY, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
        assertTrue(decision.getReason().contains("快照=失联"));
    }

    @Test
    void classify_ShouldRequireSlightlyHigherOpportunityThresholdDuringOverheatState() {
        AStockRss notice = buildNotice(
                "某公司:关于获得注册证书的公告",
                "产品获批",
                "利多",
                79
        );

        AStockPushDecision decision = pushPolicyService.classify(
                notice,
                LocalDateTime.of(2026, 3, 18, 10, 18),
                snapshot(MarketState.OVERHEAT)
        );

        assertEquals(AStockPushType.REPORT_ONLY, decision.getPushType());
        assertFalse(decision.shouldSendRealtime());
    }

    @Test
    void refineRealtimeDecision_ShouldBlockFollowerDuringOverheatState() {
        AStockPushDecision decision = new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", false, true);

        AStockPushDecision refined = pushPolicyService.refineRealtimeDecision(
                buildNotice("某公司:关于签署战略合作协议的公告", "重大合同", "利多", 91),
                decision,
                snapshot(MarketState.OVERHEAT),
                AStockRealtimeContext.empty(),
                new AReportOpportunityInsight("000001", "测试公司", "高弹性跟风", "题材映射", "等分歧确认", 60, false)
        );

        assertEquals(AStockPushType.REPORT_ONLY, refined.getPushType());
        assertFalse(refined.shouldSendRealtime());
        assertTrue(refined.getReason().contains("高潮态仅放行领军核心"));
    }

    @Test
    void refineRealtimeDecision_ShouldKeepLeaderDuringOverheatState() {
        AStockPushDecision decision = new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", false, true);
        AStockRealtimeContext context = new AStockRealtimeContext("低空经济", "低空基建提速", "政策扶持", 90, 146, "命中主线", "EXPLICIT");

        AStockPushDecision refined = pushPolicyService.refineRealtimeDecision(
                buildNotice("航天彩虹:关于签订8亿元无人机海外订单的公告", "重大合同", "利多", 91),
                decision,
                snapshot(MarketState.OVERHEAT),
                context,
                new AReportOpportunityInsight("000001", "测试公司", "领军核心", "主线级共振", "看换手承接", 90, true)
        );

        assertEquals(AStockPushType.REALTIME_OPPORTUNITY, refined.getPushType());
        assertTrue(refined.shouldSendRealtime());
        assertTrue(refined.getReason().contains("身位=领军核心"));
    }

    private AStockRss buildNotice(String title, String eventType, String signalSide, int signalScore) {
        AStockRss notice = new AStockRss();
        notice.setStockCode("000001");
        notice.setStockName("测试公司");
        notice.setTitle(title);
        notice.setEventType(eventType);
        notice.setSignalSide(signalSide);
        notice.setSignalScore(signalScore);
        return notice;
    }

    private MarketSnapshot snapshot(MarketState marketState) {
        return snapshot(marketState, MarketSnapshotHealth.LIVE, "TEST", 0);
    }

    private MarketSnapshot snapshot(MarketState marketState,
                                    MarketSnapshotHealth health,
                                    String source,
                                    int breadthSampleSize) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 18, 10, 0), source);
        snapshot.setMarketState(marketState);
        snapshot.setSnapshotHealth(health);
        snapshot.setBreadthSampleSize(breadthSampleSize);
        return snapshot;
    }
}
