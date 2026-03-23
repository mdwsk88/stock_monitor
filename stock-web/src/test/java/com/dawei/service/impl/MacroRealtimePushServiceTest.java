package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroRealtimePushServiceTest {

    @Mock
    private MacroThemeEventMapper macroThemeEventMapper;
    @Mock
    private MacroThemeStockRelMapper macroThemeStockRelMapper;
    @Mock
    private AStockPushLogService aStockPushLogService;
    @Mock
    private MarketStateService marketStateService;
    @Mock
    private WeComApi weComApi;

    private MacroRealtimePushService service;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setMacroRealtimeRiskThreshold(82);
        filterConfig.setMacroRealtimeRiskThresholdDefensive(78);
        filterConfig.setMacroRealtimeOpportunityThreshold(92);
        filterConfig.setMacroRealtimeOpportunityThresholdRiskOn(86);
        filterConfig.setMacroRealtimeOpportunityThresholdOverheat(90);
        filterConfig.setMacroRealtimePushCooldownMinutes(90);
        filterConfig.setMacroRealtimeScanWindowMinutes(60);
        filterConfig.setMarketBreadthSampleWarnThreshold(200);
        service = new MacroRealtimePushService(
                macroThemeEventMapper,
                macroThemeStockRelMapper,
                aStockPushLogService,
                marketStateService,
                weComApi,
                filterConfig
        );
    }

    @Test
    void handlePersistedEvent_ShouldSendRiskAlert() {
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.DEFENSIVE));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.MACRO_REALTIME_RISK), any())).thenReturn(false);
        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of(sampleRel("600030", "中信证券")));

        boolean pushed = service.handlePersistedEvent(riskEvent());

        assertTrue(pushed);
        verify(weComApi).sendMarkdownMessage(any(), eq(WeComApi.MarketType.A));
        ArgumentCaptor<AStockPushLog> logCaptor = ArgumentCaptor.forClass(AStockPushLog.class);
        verify(aStockPushLogService).recordPush(logCaptor.capture());
        assertEquals(AStockPushType.MACRO_REALTIME_RISK.name(), logCaptor.getValue().getPushType());
    }

    @Test
    void handlePersistedEvent_ShouldSuppressWeakOpportunityInDefensiveState() {
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.DEFENSIVE));

        boolean pushed = service.handlePersistedEvent(opportunityEvent(96));

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
    }

    @Test
    void scanAndPushRecentEvents_ShouldReturnAggregatedCounts() {
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.RISK_ON));
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of(opportunityEvent(90), riskEvent()));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.MACRO_REALTIME_OPPORTUNITY), any())).thenReturn(false);
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.MACRO_REALTIME_RISK), any())).thenReturn(true);
        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of());

        MacroRealtimePushScanResult result = service.scanAndPushRecentEvents();

        assertEquals(2, result.getScannedCount());
        assertEquals(1, result.getPushedCount());
        assertEquals(1, result.getSkippedCount());
        verify(weComApi, times(1)).sendMarkdownMessage(any(), eq(WeComApi.MarketType.A));
    }

    private MarketSnapshot snapshot(MarketState state) {
        return snapshot(state, MarketSnapshotHealth.LIVE, "TEST", 0);
    }

    @Test
    void handlePersistedEvent_ShouldNotTrustRiskOnWhenSnapshotDisconnected() {
        when(marketStateService.getLatestSnapshot()).thenReturn(
                snapshot(MarketState.RISK_ON, MarketSnapshotHealth.DISCONNECTED, "TENCENT_QUOTE+NO_BREADTH", 0)
        );

        boolean pushed = service.handlePersistedEvent(opportunityEvent(88));

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
    }

    private MarketSnapshot snapshot(MarketState state,
                                    MarketSnapshotHealth health,
                                    String source,
                                    int breadthSampleSize) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 23, 10, 0), source);
        snapshot.setMarketState(state);
        snapshot.setSnapshotHealth(health);
        snapshot.setBreadthSampleSize(breadthSampleSize);
        snapshot.setShChangePct(state == MarketState.DEFENSIVE ? -1.8d : 1.6d);
        snapshot.setSzChangePct(state == MarketState.DEFENSIVE ? -2.3d : 2.0d);
        snapshot.setCybChangePct(state == MarketState.DEFENSIVE ? -2.8d : 2.5d);
        return snapshot;
    }

    private MacroThemeEvent riskEvent() {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId("macro-risk-1");
        event.setNewsKey("macro-risk-1");
        event.setClusterKey("macro-risk-cluster");
        event.setThemeName("资本市场监管");
        event.setEventType("监管收紧");
        event.setSignalSide("利空");
        event.setSignalScore(94);
        event.setSourceName("中国证监会");
        event.setTitle("证监会就高频交易监管从严表态");
        event.setSummary("监管口径从严，市场风险偏好承压。");
        event.setPubDate(LocalDateTime.of(2026, 3, 23, 9, 35));
        return event;
    }

    private MacroThemeEvent opportunityEvent(int score) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId("macro-opportunity-1");
        event.setNewsKey("macro-opportunity-1");
        event.setClusterKey("macro-opportunity-cluster");
        event.setThemeName("国家政策");
        event.setEventType("政策发布");
        event.setSignalSide("利多");
        event.setSignalScore(score);
        event.setSourceName("中国政府网");
        event.setTitle("国务院发布支持算力基础设施建设方案");
        event.setSummary("加大支持力度，推动人工智能和算力产业发展。");
        event.setPubDate(LocalDateTime.of(2026, 3, 23, 9, 20));
        return event;
    }

    private MacroThemeStockRel sampleRel(String stockCode, String stockName) {
        MacroThemeStockRel rel = new MacroThemeStockRel();
        rel.setStockCode(stockCode);
        rel.setStockName(stockName);
        rel.setConfidence(95);
        return rel;
    }
}
