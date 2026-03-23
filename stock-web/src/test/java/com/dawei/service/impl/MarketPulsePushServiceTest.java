package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPulsePushServiceTest {

    @Mock
    private MarketStateService marketStateService;
    @Mock
    private AStockPushLogService aStockPushLogService;
    @Mock
    private WeComApi weComApi;

    private MarketPulsePushService marketPulsePushService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setMarketPulseCooldownMinutes(20);
        marketPulsePushService = new MarketPulsePushService(
                marketStateService,
                aStockPushLogService,
                weComApi,
                filterConfig
        );
    }

    @Test
    void refreshAndPushIfNeeded_ShouldSkipNeutralState() {
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot(MarketState.NEUTRAL));

        boolean pushed = marketPulsePushService.refreshAndPushIfNeeded();

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
    }

    @Test
    void refreshAndPushIfNeeded_ShouldSendDefensivePulse() {
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot(MarketState.DEFENSIVE));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.MARKET_PULSE_RISK), any()))
                .thenReturn(false);

        boolean pushed = marketPulsePushService.refreshAndPushIfNeeded();

        assertTrue(pushed);
        verify(weComApi).sendMarkdownMessage(any(), eq(WeComApi.MarketType.A));
        ArgumentCaptor<AStockPushLog> captor = ArgumentCaptor.forClass(AStockPushLog.class);
        verify(aStockPushLogService).recordPush(captor.capture());
        assertTrue(captor.getValue().getPushKey().contains("DEFENSIVE"));
    }

    @Test
    void refreshAndPushIfNeeded_ShouldRespectCooldown() {
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot(MarketState.RISK_ON));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.MARKET_PULSE_OPPORTUNITY), any()))
                .thenReturn(true);

        boolean pushed = marketPulsePushService.refreshAndPushIfNeeded();

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
    }

    @Test
    void refreshAndPushIfNeeded_ShouldSkipFallbackSnapshot() {
        MarketSnapshot snapshot = snapshot(MarketState.DEFENSIVE);
        snapshot.setSnapshotHealth(MarketSnapshotHealth.DEGRADED);
        snapshot.setFallback(true);
        snapshot.setConsecutiveFailureCount(2);
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot);

        boolean pushed = marketPulsePushService.refreshAndPushIfNeeded();

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
    }

    private MarketSnapshot snapshot(MarketState marketState) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 23, 10, 0), "TEST");
        snapshot.setMarketState(marketState);
        snapshot.setShChangePct(marketState == MarketState.DEFENSIVE ? -2.1d : 1.8d);
        snapshot.setSzChangePct(marketState == MarketState.DEFENSIVE ? -3.0d : 2.2d);
        snapshot.setCybChangePct(marketState == MarketState.DEFENSIVE ? -3.4d : 2.7d);
        snapshot.setUpCount(marketState == MarketState.DEFENSIVE ? 300 : 3800);
        snapshot.setDownCount(marketState == MarketState.DEFENSIVE ? 4700 : 1200);
        snapshot.setLimitUpCount(marketState == MarketState.DEFENSIVE ? 8 : 95);
        snapshot.setLimitDownCount(marketState == MarketState.DEFENSIVE ? 140 : 4);
        return snapshot;
    }
}
