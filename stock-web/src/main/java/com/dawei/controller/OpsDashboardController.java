package com.dawei.controller;

import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.OpsDashboardSnapshot;
import com.dawei.service.MarketStateService;
import com.dawei.service.impl.AStockPushHealthAlertService;
import com.dawei.service.impl.MacroRealtimePushService;
import com.dawei.service.impl.OpsDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 运营看板接口。
 */
@RestController
@RequestMapping("/api/ops-dashboard")
public class OpsDashboardController {

    private final OpsDashboardService opsDashboardService;
    private final MarketStateService marketStateService;
    private final AStockPushHealthAlertService aStockPushHealthAlertService;
    private final MacroRealtimePushService macroRealtimePushService;

    public OpsDashboardController(OpsDashboardService opsDashboardService,
                                  MarketStateService marketStateService,
                                  AStockPushHealthAlertService aStockPushHealthAlertService,
                                  MacroRealtimePushService macroRealtimePushService) {
        this.opsDashboardService = opsDashboardService;
        this.marketStateService = marketStateService;
        this.aStockPushHealthAlertService = aStockPushHealthAlertService;
        this.macroRealtimePushService = macroRealtimePushService;
    }

    @GetMapping("/summary")
    public OpsDashboardSnapshot getSummary() {
        return opsDashboardService.buildSnapshot();
    }

    @PostMapping("/actions/refresh-market")
    public Map<String, Object> refreshMarketSnapshot() {
        MarketSnapshot snapshot = marketStateService.refreshSnapshot();
        boolean live = snapshot != null
                && snapshot.getSnapshotHealth() != null
                && snapshot.getSnapshotHealth() == MarketSnapshotHealth.LIVE
                && !snapshot.isFallback();
        Map<String, Object> result = new HashMap<>();
        result.put("success", live);
        result.put("message", resolveRefreshMessage(snapshot, live));
        result.put("marketSnapshot", snapshot);
        return result;
    }

    @PostMapping("/actions/run-health-check")
    public Map<String, Object> runHealthCheck() {
        AStockPushHealthCheckResult checkResult = aStockPushHealthAlertService.inspectAndPushIfNeeded();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", checkResult != null && checkResult.isAlertTriggered()
                ? "健康巡检发现异常，结果已保留，不再推送企业微信"
                : "健康巡检完成，当前未触发健康告警");
        result.put("data", checkResult);
        return result;
    }

    @PostMapping("/actions/run-macro-scan")
    public Map<String, Object> runMacroScan() {
        MacroRealtimePushScanResult scanResult = macroRealtimePushService.scanAndPushRecentEvents();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", scanResult != null && scanResult.getPushedCount() != null && scanResult.getPushedCount() > 0
                ? "宏观快讯实时回扫已触发推送"
                : "宏观快讯回扫完成，当前没有新增实时推送");
        result.put("data", scanResult);
        return result;
    }

    private String resolveRefreshMessage(MarketSnapshot snapshot, boolean live) {
        if (live) {
            return "市场状态快照已刷新";
        }
        if (snapshot != null && snapshot.getSnapshotHealth() == MarketSnapshotHealth.DEGRADED) {
            return "市场状态刷新失败，已回退到缓存快照";
        }
        return "市场状态刷新失败，当前快照已失联";
    }
}
