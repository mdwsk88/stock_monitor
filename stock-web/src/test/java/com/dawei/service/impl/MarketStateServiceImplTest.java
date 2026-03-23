package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.service.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketStateServiceImplTest {

    private MarketDataProvider marketDataProvider;
    private MarketStateServiceImpl marketStateService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setMarketDefensiveIndexDropThreshold(-1.5d);
        filterConfig.setMarketDefensiveBreadthThreshold(0.35d);
        filterConfig.setMarketDefensiveLimitDownThreshold(80);
        filterConfig.setMarketRiskOnIndexRiseThreshold(1.2d);
        filterConfig.setMarketRiskOnBreadthThreshold(0.60d);
        filterConfig.setMarketOverheatIndexRiseThreshold(2.4d);
        filterConfig.setMarketOverheatBreadthThreshold(0.72d);
        filterConfig.setMarketOverheatLimitUpThreshold(80);
        filterConfig.setMarketSnapshotRefreshMinutes(5);
        filterConfig.setMarketSnapshotFailureCooldownSeconds(60);
        filterConfig.setMarketSnapshotDisconnectFailureThreshold(3);
        marketDataProvider = Mockito.mock(MarketDataProvider.class);
        marketStateService = new MarketStateServiceImpl(marketDataProvider, filterConfig);
    }

    @Test
    void refreshSnapshot_ShouldResolveDefensiveState() {
        MarketSnapshot snapshot = buildSnapshot(-2.1d, -2.6d, -3.0d, 400, 4700, 12, 180);
        when(marketDataProvider.fetchSnapshot()).thenReturn(snapshot);

        MarketSnapshot refreshed = marketStateService.refreshSnapshot();

        assertEquals(MarketState.DEFENSIVE, refreshed.getMarketState());
        assertEquals(MarketSnapshotHealth.LIVE, refreshed.getSnapshotHealth());
        assertNotNull(refreshed.getLastSuccessAt());
    }

    @Test
    void refreshSnapshot_ShouldResolveRiskOnState() {
        MarketSnapshot snapshot = buildSnapshot(1.4d, 1.8d, 2.1d, 3600, 1500, 66, 8);
        when(marketDataProvider.fetchSnapshot()).thenReturn(snapshot);

        MarketSnapshot refreshed = marketStateService.refreshSnapshot();

        assertEquals(MarketState.RISK_ON, refreshed.getMarketState());
    }

    @Test
    void refreshSnapshot_ShouldResolveOverheatState() {
        MarketSnapshot snapshot = buildSnapshot(1.9d, 2.2d, 2.8d, 4200, 900, 110, 4);
        when(marketDataProvider.fetchSnapshot()).thenReturn(snapshot);

        MarketSnapshot refreshed = marketStateService.refreshSnapshot();

        assertEquals(MarketState.OVERHEAT, refreshed.getMarketState());
    }

    @Test
    void refreshSnapshot_ShouldFallbackToCachedSnapshotWhenProviderFails() {
        MarketSnapshot snapshot = buildSnapshot(1.4d, 1.8d, 2.1d, 3600, 1500, 66, 8);
        when(marketDataProvider.fetchSnapshot()).thenReturn(snapshot).thenThrow(new IllegalStateException("network"));
        MarketSnapshot first = marketStateService.refreshSnapshot();

        MarketSnapshot fallback = marketStateService.refreshSnapshot();

        assertEquals(first.getMarketState(), fallback.getMarketState());
        assertTrue(fallback.isFallback());
        assertEquals(MarketSnapshotHealth.DEGRADED, fallback.getSnapshotHealth());
        assertEquals(1, fallback.getConsecutiveFailureCount());
    }

    @Test
    void getLatestSnapshot_ShouldRespectFailureCooldownAfterFailure() {
        when(marketDataProvider.fetchSnapshot()).thenThrow(new IllegalStateException("network"));

        MarketSnapshot first = marketStateService.getLatestSnapshot();
        MarketSnapshot second = marketStateService.getLatestSnapshot();

        assertEquals(MarketSnapshotHealth.DISCONNECTED, first.getSnapshotHealth());
        assertEquals(MarketSnapshotHealth.DISCONNECTED, second.getSnapshotHealth());
        assertTrue(second.isFallback());
        assertNotNull(second.getNextRetryAt());
        verify(marketDataProvider, times(1)).fetchSnapshot();
    }

    private MarketSnapshot buildSnapshot(double sh, double sz, double cyb,
                                         int upCount, int downCount,
                                         int limitUp, int limitDown) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.now(), "TEST");
        snapshot.setShChangePct(sh);
        snapshot.setSzChangePct(sz);
        snapshot.setCybChangePct(cyb);
        snapshot.setUpCount(upCount);
        snapshot.setDownCount(downCount);
        snapshot.setLimitUpCount(limitUp);
        snapshot.setLimitDownCount(limitDown);
        return snapshot;
    }
}
