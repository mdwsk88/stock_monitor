package com.dawei.controller;

import com.dawei.service.impl.AStockHistoryRepairService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public MaintenanceController(AStockHistoryRepairService aStockHistoryRepairService) {
        this.aStockHistoryRepairService = aStockHistoryRepairService;
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
}
