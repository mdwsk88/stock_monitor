package com.dawei.controller;

import com.dawei.service.impl.AStockHistoryRepairService;
import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import com.dawei.service.impl.MacroNewsHistoryRepairService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 运维修复接口
 */
@Slf4j
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final AStockHistoryRepairService aStockHistoryRepairService;
    private final MacroNewsService macroNewsService;
    private final MacroNewsHistoryRepairService macroNewsHistoryRepairService;
    private final ThemeAutoPoolService themeAutoPoolService;

    public MaintenanceController(AStockHistoryRepairService aStockHistoryRepairService,
                                 MacroNewsService macroNewsService,
                                 MacroNewsHistoryRepairService macroNewsHistoryRepairService,
                                 ThemeAutoPoolService themeAutoPoolService) {
        this.aStockHistoryRepairService = aStockHistoryRepairService;
        this.macroNewsService = macroNewsService;
        this.macroNewsHistoryRepairService = macroNewsHistoryRepairService;
        this.themeAutoPoolService = themeAutoPoolService;
    }

    @PostMapping("/a-stock/repair-history")
    public Map<String, Object> repairAStockHistory() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股历史数据修复");
            AStockHistoryRepairService.RepairSummary summary = aStockHistoryRepairService.repairHistoricalNotices();
            result.put("success", true);
            result.put("message", "A股历史数据修复完成");
            result.put("summary", summary);
        } catch (Exception e) {
            log.error("手动触发A股历史数据修复失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "修复失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/macro/fetch")
    public Map<String, Object> fetchMacroNews() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发宏观新闻抓取");
            MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();
            result.put("success", true);
            result.put("message", "宏观新闻抓取完成");
            result.put("summary", summary);
        } catch (Exception e) {
            log.error("手动触发宏观新闻抓取失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "抓取失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/macro/repair-history")
    public Map<String, Object> repairMacroHistory(@RequestParam(defaultValue = "72") int hours) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发宏观历史事件修复，hours={}", hours);
            MacroNewsHistoryRepairService.RepairSummary summary =
                    macroNewsHistoryRepairService.repairRecentEvents(hours);
            result.put("success", true);
            result.put("message", "宏观历史事件修复完成");
            result.put("summary", summary);
        } catch (Exception e) {
            log.error("手动触发宏观历史事件修复失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "修复失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/macro/rebuild-auto-pool")
    public Map<String, Object> rebuildThemeAutoPool(@RequestParam(defaultValue = "720") int hours) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发主题自动候选池重建，hours={}", hours);
            ThemeAutoPoolService.RebuildSummary summary =
                    themeAutoPoolService.rebuildFromRecentExplicitRelations(hours);
            result.put("success", true);
            result.put("message", "主题自动候选池重建完成");
            result.put("summary", summary);
        } catch (Exception e) {
            log.error("手动触发主题自动候选池重建失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "重建失败: " + e.getMessage());
        }
        return result;
    }
}
