package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.entity.OpsDashboardSnapshot;
import com.dawei.mapper.AStockPushDecisionLogMapper;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.service.MarketStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsDashboardServiceTest {

    @Mock
    private AStockRssMapper aStockRssMapper;
    @Mock
    private AStockPushDecisionLogMapper aStockPushDecisionLogMapper;
    @Mock
    private AStockPushLogMapper aStockPushLogMapper;
    @Mock
    private MacroThemeEventMapper macroThemeEventMapper;
    @Mock
    private MarketStateService marketStateService;

    private OpsDashboardService opsDashboardService;

    @BeforeEach
    void setUp() {
        StockFilterConfig config = new StockFilterConfig();
        config.setRealtimeHealthWindowMinutes(120);
        config.setARealtimeSignalThreshold(70);
        config.setARealtimeRiskThresholdDefensive(70);
        config.setMacroRealtimeRiskThresholdDefensive(78);
        config.setMacroRealtimeOpportunityThresholdRiskOn(86);
        config.setRealtimeHealthHighSignalCountThreshold(2);
        config.setRealtimeHealthHardRiskCountThreshold(1);
        config.setRealtimeHealthMacroRiskCountThreshold(1);
        config.setRealtimeHealthMacroOpportunityCountThreshold(2);
        config.setRealtimeHealthDecisionCountThreshold(3);
        config.setRealtimeHealthSkippedRatioThreshold(0.8d);
        config.setRealtimeHealthFailureCountThreshold(1);
        config.setMacroShadowSignalThreshold(75);
        config.setMarketSnapshotRefreshMinutes(5);
        config.setMarketSnapshotDisconnectFailureThreshold(3);
        config.setMarketBreadthSampleWarnThreshold(200);

        opsDashboardService = new OpsDashboardService(
                aStockRssMapper,
                aStockPushDecisionLogMapper,
                aStockPushLogMapper,
                macroThemeEventMapper,
                marketStateService,
                config
        );
    }

    @Test
    void buildSnapshot_ShouldExposeWarningsAndDistributions() {
        when(aStockRssMapper.selectList(any())).thenReturn(List.of(
                notice("宁德时代", "300750", "重大订单", "利多", 92, LocalDateTime.now().minusMinutes(20)),
                notice("ST熊猫", "600599", "终止上市风险提示", "利空", 84, LocalDateTime.now().minusMinutes(30))
        ));
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of(
                decision("SKIPPED", AStockPushType.REALTIME_OPPORTUNITY.name(), "高潮态仅放行领军核心", LocalDateTime.now().minusMinutes(15)),
                decision("SKIPPED", AStockPushType.REALTIME_RISK.name(), "命中冷却期", LocalDateTime.now().minusMinutes(10)),
                decision("FAILED", AStockPushType.REALTIME_OPPORTUNITY.name(), "企业微信失败", LocalDateTime.now().minusMinutes(5))
        ));
        when(aStockPushLogMapper.selectList(any())).thenReturn(List.of(
                push(AStockPushType.REALTIME_HEALTH_ALERT.name(), "健康告警", LocalDateTime.now().minusMinutes(8)),
                push(AStockPushType.MARKET_PULSE_RISK.name(), "防守脉冲", LocalDateTime.now().minusMinutes(18))
        ));
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of(
                macro("资本市场监管", "监管收紧", "利空", 94, LocalDateTime.now().minusMinutes(25)),
                macro("国家政策", "政策发布", "利多", 90, LocalDateTime.now().minusMinutes(12))
        ));
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.DEFENSIVE));

        OpsDashboardSnapshot snapshot = opsDashboardService.buildSnapshot();

        assertEquals("CRITICAL", snapshot.getHealthLevel());
        assertFalse(snapshot.getWarnings().isEmpty());
        assertEquals(2, snapshot.getMetrics().getHighSignalNoticeCount());
        assertEquals(0, snapshot.getMetrics().getSentCount());
        assertEquals(1, snapshot.getMetrics().getHealthAlertCount());
        assertTrue(snapshot.getPushTypeDistribution().containsKey(AStockPushType.REALTIME_HEALTH_ALERT.name()));
        assertEquals(2, snapshot.getTimeline().size() > 0 ? snapshot.getTimeline().stream().mapToInt(point -> point.getHighSignalNoticeCount()).sum() : 0);
        assertFalse(snapshot.getTopSilentReasons().isEmpty());
    }

    @Test
    void buildSnapshot_ShouldBeHealthyWhenDataBalanced() {
        when(aStockRssMapper.selectList(any())).thenReturn(List.of(
                notice("中际旭创", "300308", "订单进展", "利多", 82, LocalDateTime.now().minusMinutes(15))
        ));
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of(
                decision("SENT", AStockPushType.REALTIME_OPPORTUNITY.name(), "主线机会直推", LocalDateTime.now().minusMinutes(14))
        ));
        when(aStockPushLogMapper.selectList(any())).thenReturn(List.of(
                push(AStockPushType.REALTIME_OPPORTUNITY.name(), "盘中机会", LocalDateTime.now().minusMinutes(14))
        ));
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of());
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot(MarketState.RISK_ON));

        OpsDashboardSnapshot snapshot = opsDashboardService.buildSnapshot();

        assertEquals("HEALTHY", snapshot.getHealthLevel());
        assertTrue(snapshot.getWarnings().isEmpty());
        assertEquals(1, snapshot.getMetrics().getSentCount());
        assertEquals(0, snapshot.getMetrics().getSkippedCount());
    }

    @Test
    void buildSnapshot_ShouldSurfaceDisconnectedMarketSnapshot() {
        when(aStockRssMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushLogMapper.selectList(any())).thenReturn(List.of());
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of());
        MarketSnapshot marketSnapshot = snapshot(MarketState.NEUTRAL);
        marketSnapshot.setSnapshotHealth(MarketSnapshotHealth.DISCONNECTED);
        marketSnapshot.setFallback(true);
        marketSnapshot.setConsecutiveFailureCount(3);
        marketSnapshot.setLastFailureAt(LocalDateTime.now().minusMinutes(1));
        marketSnapshot.setCapturedAt(null);
        when(marketStateService.getLatestSnapshot()).thenReturn(marketSnapshot);

        OpsDashboardSnapshot snapshot = opsDashboardService.buildSnapshot();

        assertEquals("CRITICAL", snapshot.getHealthLevel());
        assertTrue(snapshot.getWarnings().stream().anyMatch(item -> item.contains("市场快照已失联")));
    }

    @Test
    void buildSnapshot_ShouldWarnWhenBreadthUnavailable() {
        when(aStockRssMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushDecisionLogMapper.selectList(any())).thenReturn(List.of());
        when(aStockPushLogMapper.selectList(any())).thenReturn(List.of());
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of());
        MarketSnapshot marketSnapshot = snapshot(MarketState.RISK_ON);
        marketSnapshot.setSource("TENCENT_QUOTE+NO_BREADTH");
        when(marketStateService.getLatestSnapshot()).thenReturn(marketSnapshot);

        OpsDashboardSnapshot snapshot = opsDashboardService.buildSnapshot();

        assertEquals("WARN", snapshot.getHealthLevel());
        assertTrue(snapshot.getWarnings().stream().anyMatch(item -> item.contains("市场宽度当前不可用")));
    }

    private AStockRss notice(String stockName, String stockCode, String title, String signalSide, int score, LocalDateTime pubDate) {
        AStockRss rss = new AStockRss();
        rss.setStockName(stockName);
        rss.setStockCode(stockCode);
        rss.setTitle(title);
        rss.setSignalSide(signalSide);
        rss.setSignalScore(score);
        rss.setPubDate(pubDate);
        return rss;
    }

    private AStockPushDecisionLog decision(String status, String pushType, String reason, LocalDateTime time) {
        AStockPushDecisionLog log = new AStockPushDecisionLog();
        log.setSendStatus(status);
        log.setPushType(pushType);
        log.setDecisionReason(reason);
        log.setTitle("测试标题");
        log.setDecidedAt(time);
        log.setStockCode("300750");
        log.setStockName("宁德时代");
        return log;
    }

    private AStockPushLog push(String pushType, String reason, LocalDateTime time) {
        AStockPushLog log = new AStockPushLog();
        log.setPushType(pushType);
        log.setDecisionReason(reason);
        log.setTitle("测试推送");
        log.setPushedAt(time);
        return log;
    }

    private MacroThemeEvent macro(String themeName, String eventType, String signalSide, int score, LocalDateTime time) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setThemeName(themeName);
        event.setEventType(eventType);
        event.setSignalSide(signalSide);
        event.setSignalScore(score);
        event.setTitle("宏观快讯");
        event.setPubDate(time);
        return event;
    }

    private MarketSnapshot snapshot(MarketState marketState) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.now().minusMinutes(3), "TEST");
        snapshot.setMarketState(marketState);
        snapshot.setShChangePct(marketState == MarketState.DEFENSIVE ? -1.6d : 1.4d);
        snapshot.setSzChangePct(marketState == MarketState.DEFENSIVE ? -2.3d : 1.9d);
        snapshot.setCybChangePct(marketState == MarketState.DEFENSIVE ? -2.8d : 2.3d);
        snapshot.setUpCount(marketState == MarketState.DEFENSIVE ? 400 : 3600);
        snapshot.setDownCount(marketState == MarketState.DEFENSIVE ? 4700 : 1300);
        snapshot.setFlatCount(200);
        snapshot.setLimitUpCount(marketState == MarketState.DEFENSIVE ? 6 : 70);
        snapshot.setLimitDownCount(marketState == MarketState.DEFENSIVE ? 110 : 3);
        return snapshot;
    }
}
