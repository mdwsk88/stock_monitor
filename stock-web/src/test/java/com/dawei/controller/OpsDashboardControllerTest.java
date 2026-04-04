package com.dawei.controller;

import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.entity.OpsDashboardSnapshot;
import com.dawei.service.MarketStateService;
import com.dawei.service.impl.AStockPushHealthAlertService;
import com.dawei.service.impl.MacroRealtimePushService;
import com.dawei.service.impl.OpsDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsDashboardControllerTest {

    @Mock
    private OpsDashboardService opsDashboardService;
    @Mock
    private MarketStateService marketStateService;
    @Mock
    private AStockPushHealthAlertService aStockPushHealthAlertService;
    @Mock
    private MacroRealtimePushService macroRealtimePushService;

    @InjectMocks
    private OpsDashboardController opsDashboardController;

    @Test
    @DisplayName("测试获取运营看板摘要")
    void testGetSummary() {
        OpsDashboardSnapshot snapshot = new OpsDashboardSnapshot();
        snapshot.setHealthLevel("WARN");
        when(opsDashboardService.buildSnapshot()).thenReturn(snapshot);

        OpsDashboardSnapshot result = opsDashboardController.getSummary();

        assertSame(snapshot, result);
        verify(opsDashboardService).buildSnapshot();
    }

    @Test
    @DisplayName("测试手动刷新市场快照")
    void testRefreshMarketSnapshot() {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.now(), "TEST");
        snapshot.setMarketState(MarketState.RISK_ON);
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot);

        Map<String, Object> result = opsDashboardController.refreshMarketSnapshot();

        assertTrue((Boolean) result.get("success"));
        assertEquals("市场状态快照已刷新", result.get("message"));
        assertSame(snapshot, result.get("marketSnapshot"));
    }

    @Test
    @DisplayName("测试手动刷新市场快照降级回退")
    void testRefreshMarketSnapshotWhenFallback() {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.now().minusMinutes(3), "TEST");
        snapshot.setMarketState(MarketState.DEFENSIVE);
        snapshot.setSnapshotHealth(MarketSnapshotHealth.DEGRADED);
        snapshot.setFallback(true);
        when(marketStateService.refreshSnapshot()).thenReturn(snapshot);

        Map<String, Object> result = opsDashboardController.refreshMarketSnapshot();

        assertEquals(false, result.get("success"));
        assertEquals("市场状态刷新失败，已回退到缓存快照", result.get("message"));
        assertSame(snapshot, result.get("marketSnapshot"));
    }

    @Test
    @DisplayName("测试执行健康巡检动作")
    void testRunHealthCheck() {
        AStockPushHealthCheckResult resultObject = new AStockPushHealthCheckResult();
        resultObject.setAlertTriggered(true);
        resultObject.setPushed(false);
        when(aStockPushHealthAlertService.inspectAndPushIfNeeded()).thenReturn(resultObject);

        Map<String, Object> result = opsDashboardController.runHealthCheck();

        assertTrue((Boolean) result.get("success"));
        assertEquals("健康巡检发现异常，结果已保留，不再推送企业微信", result.get("message"));
        assertSame(resultObject, result.get("data"));
    }

    @Test
    @DisplayName("测试执行宏观回扫动作")
    void testRunMacroScan() {
        MacroRealtimePushScanResult scanResult = new MacroRealtimePushScanResult();
        scanResult.setPushedCount(2);
        when(macroRealtimePushService.scanAndPushRecentEvents()).thenReturn(scanResult);

        Map<String, Object> result = opsDashboardController.runMacroScan();

        assertTrue((Boolean) result.get("success"));
        assertEquals("宏观快讯实时回扫已触发推送", result.get("message"));
        assertSame(scanResult, result.get("data"));
    }
}
