package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
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
        filterConfig.setARealtimeRiskThreshold(88);
        filterConfig.setARealtimeCriticalThreshold(92);
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
}
