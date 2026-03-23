package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockPushHealthAlertServiceTest {

    @Mock
    private AStockRssMapper aStockRssMapper;
    @Mock
    private AStockPushDecisionLogMapper aStockPushDecisionLogMapper;
    @Mock
    private AStockPushLogMapper aStockPushLogMapper;
    @Mock
    private MacroThemeEventMapper macroThemeEventMapper;
    @Mock
    private AStockPushLogService aStockPushLogService;
    @Mock
    private MarketStateService marketStateService;
    @Mock
    private WeComApi weComApi;

    private AStockPushHealthAlertService service;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setARealtimeSignalThreshold(70);
        filterConfig.setARealtimeRiskThresholdDefensive(70);
        filterConfig.setRealtimeHealthWindowMinutes(120);
        filterConfig.setRealtimeHealthHighSignalCountThreshold(8);
        filterConfig.setRealtimeHealthHardRiskCountThreshold(2);
        filterConfig.setRealtimeHealthDecisionCountThreshold(8);
        filterConfig.setRealtimeHealthSkippedRatioThreshold(0.9d);
        filterConfig.setRealtimeHealthFailureCountThreshold(2);
        filterConfig.setRealtimeHealthMacroRiskCountThreshold(2);
        filterConfig.setRealtimeHealthMacroOpportunityCountThreshold(3);
        filterConfig.setRealtimeHealthCooldownMinutes(30);
        filterConfig.setMarketBreadthSampleWarnThreshold(200);
        service = new AStockPushHealthAlertService(
                aStockRssMapper,
                aStockPushDecisionLogMapper,
                aStockPushLogMapper,
                macroThemeEventMapper,
                aStockPushLogService,
                marketStateService,
                weComApi,
                filterConfig
        );
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.DEFENSIVE));
    }

    @Test
    void inspectAndPushIfNeeded_ShouldSkipHealthyWindow() {
        when(aStockRssMapper.selectCount(any())).thenReturn(3L, 0L);
        when(aStockPushDecisionLogMapper.selectCount(any())).thenReturn(3L, 1L, 2L, 0L, 0L);
        when(macroThemeEventMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockPushLogMapper.selectCount(any())).thenReturn(0L, 0L);

        AStockPushHealthCheckResult result = service.inspectAndPushIfNeeded();

        assertFalse(result.isAlertTriggered());
        assertFalse(result.isPushed());
        verify(aStockPushLogService, never()).hasRecentPush(any(), any(), any());
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
    }

    @Test
    void inspectAndPushIfNeeded_ShouldSendSilenceAlertWhenRiskBlindSpotDetected() {
        when(aStockRssMapper.selectCount(any())).thenReturn(11L, 3L);
        when(aStockPushDecisionLogMapper.selectCount(any())).thenReturn(10L, 0L, 10L, 0L, 0L);
        when(macroThemeEventMapper.selectCount(any())).thenReturn(2L, 1L);
        when(aStockPushLogMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockRssMapper.selectList(any())).thenReturn(List.of(sampleNotice("宁德时代", "300750", "重大订单签约", "利多", 92)));
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of(sampleDecision("中际旭创", "300308", "状态机降级为观察名单", "SKIPPED")));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_HEALTH_ALERT), any())).thenReturn(false);

        AStockPushHealthCheckResult result = service.inspectAndPushIfNeeded();

        assertTrue(result.isAlertTriggered());
        assertTrue(result.isPushed());
        ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
        verify(weComApi).sendMarkdownMessage(markdownCaptor.capture(), eq(WeComApi.MarketType.A));
        assertTrue(markdownCaptor.getValue().contains("健康告警"));
        ArgumentCaptor<AStockPushLog> logCaptor = ArgumentCaptor.forClass(AStockPushLog.class);
        verify(aStockPushLogService).recordPush(logCaptor.capture());
        assertTrue(logCaptor.getValue().getPushType().contains("REALTIME_HEALTH_ALERT"));
        assertTrue(logCaptor.getValue().getEventType().contains("健康告警"));
    }

    @Test
    void inspectAndPushIfNeeded_ShouldRespectCooldown() {
        when(aStockRssMapper.selectCount(any())).thenReturn(9L, 2L);
        when(aStockPushDecisionLogMapper.selectCount(any())).thenReturn(8L, 0L, 8L, 0L, 0L);
        when(macroThemeEventMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockPushLogMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockRssMapper.selectList(any())).thenReturn(List.of(sampleNotice("比亚迪", "002594", "发布核心车型订单", "利多", 88)));
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of(sampleDecision("比亚迪", "002594", "命中冷却期", "SKIPPED")));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_HEALTH_ALERT), any())).thenReturn(true);

        AStockPushHealthCheckResult result = service.inspectAndPushIfNeeded();

        assertTrue(result.isAlertTriggered());
        assertFalse(result.isPushed());
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
    }

    @Test
    void inspectAndPushIfNeeded_ShouldSendHealthAlertWhenSnapshotDisconnected() {
        MarketSnapshot disconnected = snapshot(MarketState.NEUTRAL);
        disconnected.setSnapshotHealth(MarketSnapshotHealth.DISCONNECTED);
        disconnected.setSource("TENCENT_QUOTE+NO_BREADTH");
        disconnected.setFallback(true);
        disconnected.setCapturedAt(null);
        disconnected.setConsecutiveFailureCount(4);
        disconnected.setLastFailureAt(LocalDateTime.of(2026, 3, 23, 10, 36));
        disconnected.setLastFailureReason("upstream EOF");
        when(marketStateService.getLatestSnapshot()).thenReturn(disconnected);
        when(aStockRssMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockPushDecisionLogMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L, 0L);
        when(macroThemeEventMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockPushLogMapper.selectCount(any())).thenReturn(0L, 0L);
        when(aStockRssMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_HEALTH_ALERT), any())).thenReturn(false);

        AStockPushHealthCheckResult result = service.inspectAndPushIfNeeded();

        assertTrue(result.isAlertTriggered());
        assertTrue(result.isPushed());
        assertEquals(MarketSnapshotHealth.DISCONNECTED, result.getSnapshotHealth());
        assertEquals("TENCENT_QUOTE+NO_BREADTH", result.getSnapshotSource());
        assertEquals(4, result.getSnapshotConsecutiveFailureCount());
        verify(weComApi).sendMarkdownMessage(argThat(markdown ->
                        markdown.contains("市场快照已失联")
                                && markdown.contains("市场宽度当前不可用")
                                && markdown.contains("TENCENT_QUOTE+NO_BREADTH")),
                eq(WeComApi.MarketType.A));
    }

    private MarketSnapshot snapshot(MarketState marketState) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 23, 10, 30), "TEST");
        snapshot.setMarketState(marketState);
        snapshot.setShChangePct(-2.0d);
        snapshot.setSzChangePct(-2.8d);
        snapshot.setCybChangePct(-3.2d);
        return snapshot;
    }

    private AStockRss sampleNotice(String stockName, String stockCode, String title, String signalSide, Integer score) {
        AStockRss notice = new AStockRss();
        notice.setStockName(stockName);
        notice.setStockCode(stockCode);
        notice.setTitle(title);
        notice.setSignalSide(signalSide);
        notice.setSignalScore(score);
        return notice;
    }

    private AStockPushDecisionLog sampleDecision(String stockName, String stockCode, String reason, String status) {
        AStockPushDecisionLog log = new AStockPushDecisionLog();
        log.setStockName(stockName);
        log.setStockCode(stockCode);
        log.setDecisionReason(reason);
        log.setSendStatus(status);
        return log;
    }
}
