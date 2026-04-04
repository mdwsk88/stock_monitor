package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MarketAlertFamily;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.service.AStockPushLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketAlertCoordinatorServiceTest {

    @Mock
    private AStockPushLogService aStockPushLogService;

    private MarketAlertCoordinatorService coordinatorService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setMarketAlertFamilyCooldownMinutes(120);
        filterConfig.setMarketPanicIndexDropThreshold(-3.0d);
        filterConfig.setMarketPanicLimitDownThreshold(150);
        filterConfig.setMarketPanicBreadthThreshold(0.25d);
        coordinatorService = new MarketAlertCoordinatorService(aStockPushLogService, filterConfig);
    }

    @Test
    void evaluate_ShouldSuppressCounterTrendMacroAlert() {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 4, 1, 10, 0), "TEST");
        snapshot.setMarketState(MarketState.RISK_ON);

        MarketAlertCoordinatorService.AlertDispatchDecision decision = coordinatorService.evaluate(
                MarketAlertFamily.BEARISH,
                AStockPushType.MACRO_REALTIME_RISK,
                "macro-realtime|risk",
                snapshot,
                95,
                LocalDateTime.of(2026, 4, 1, 10, 5)
        );

        assertFalse(decision.allowed());
    }

    @Test
    void evaluate_ShouldAllowEscalationToOverheatWithinBullishFamily() {
        AStockPushLog latestPush = new AStockPushLog();
        latestPush.setPushType(AStockPushType.MARKET_PULSE_OPPORTUNITY.name());
        latestPush.setPushKey("market-pulse|RISK_ON");
        latestPush.setSignalScore(95);
        when(aStockPushLogService.findLatestPush(eq(List.of(
                AStockPushType.MARKET_PULSE_OPPORTUNITY,
                AStockPushType.MACRO_REALTIME_OPPORTUNITY
        )), any())).thenReturn(latestPush);

        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 4, 1, 11, 0), "TEST");
        snapshot.setMarketState(MarketState.OVERHEAT);

        MarketAlertCoordinatorService.AlertDispatchDecision decision = coordinatorService.evaluate(
                MarketAlertFamily.BULLISH,
                AStockPushType.MARKET_PULSE_OPPORTUNITY,
                "market-pulse|OVERHEAT",
                snapshot,
                110,
                LocalDateTime.of(2026, 4, 1, 11, 0)
        );

        assertTrue(decision.allowed());
    }
}
