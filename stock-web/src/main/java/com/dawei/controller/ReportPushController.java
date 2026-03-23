package com.dawei.controller;

import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.scheduler.MorningReportScheduler;
import com.dawei.service.impl.AStockPushHealthAlertService;
import com.dawei.service.impl.MacroRealtimePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName ReportPushController
 * @Author dawei
 * @Version 2.0
 * @Description 推送任务手动触发接口（用于测试）
 **/
@Slf4j
@RestController
@RequestMapping({"/api/report-push", "/api/morning-report"})
public class ReportPushController {

    private final MorningReportScheduler morningReportScheduler;
    private final AStockPushHealthAlertService aStockPushHealthAlertService;
    private final MacroRealtimePushService macroRealtimePushService;

    public ReportPushController(MorningReportScheduler morningReportScheduler,
                                AStockPushHealthAlertService aStockPushHealthAlertService,
                                MacroRealtimePushService macroRealtimePushService) {
        this.morningReportScheduler = morningReportScheduler;
        this.aStockPushHealthAlertService = aStockPushHealthAlertService;
        this.macroRealtimePushService = macroRealtimePushService;
    }

    /**
     * 手动触发美股早报（隔夜复盘）- 早上7:30
     * 数据范围：过去12小时（昨晚20:00到今早8:00）
     */
    @GetMapping("/push/us/morning")
    public Map<String, Object> pushUSMorningReport() {
        Map<String, Object> result = new HashMap<>();
        if (!morningReportScheduler.isUsPushEnabled()) {
            result.put("success", true);
            result.put("message", "美股推送当前已关闭，已跳过");
            result.put("dataRange", "过去12小时（昨晚20:00到今早8:00）");
            return result;
        }
        try {
            log.info("手动触发美股早报（隔夜复盘）");
            morningReportScheduler.pushUSMorningReportManually();
            result.put("success", true);
            result.put("message", "美股早报（隔夜复盘）推送成功");
            result.put("dataRange", "过去12小时（昨晚20:00到今早8:00）");
        } catch (Exception e) {
            log.error("手动触发美股早报失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发A股盘前早报 - 早上8:30
     * 数据范围：过去24小时（昨天8:30到今天8:30）
     */
    @GetMapping("/push/a/morning")
    public Map<String, Object> pushAMorningReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股盘前早报");
            morningReportScheduler.pushAMorningReportManually();
            result.put("success", true);
            result.put("message", "A股盘前早报推送成功");
            result.put("dataRange", "过去24小时（昨天8:30到今天8:30）");
        } catch (Exception e) {
            log.error("手动触发A股盘前早报失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发A股盘后复盘 - 下午15:30
     * 数据范围：当天9:00到15:00（过去6小时）
     */
    @GetMapping("/push/a/evening")
    public Map<String, Object> pushAEveningReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股盘后复盘");
            morningReportScheduler.pushAEveningReportManually();
            result.put("success", true);
            result.put("message", "A股盘后复盘推送成功");
            result.put("dataRange", "当天9:00到当前执行时间");
        } catch (Exception e) {
            log.error("手动触发A股盘后复盘失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发A股盘后风险速递 - 晚上18:30
     * 数据范围：当天15:00到当前执行时间
     */
    @GetMapping("/push/a/post-close-risk")
    public Map<String, Object> pushAPostCloseRiskDigest() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股盘后风险速递");
            morningReportScheduler.pushAPostCloseRiskDigestManually();
            result.put("success", true);
            result.put("message", "A股盘后风险速递推送成功");
            result.put("dataRange", "当天15:00到当前执行时间");
        } catch (Exception e) {
            log.error("手动触发A股盘后风险速递失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发A股实时链路健康巡检
     */
    @GetMapping("/push/a/realtime-health")
    public Map<String, Object> pushARealtimeHealthAlert() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股实时链路健康巡检");
            AStockPushHealthCheckResult checkResult = aStockPushHealthAlertService.inspectAndPushIfNeeded();
            result.put("success", true);
            result.put("message", resolveHealthAlertMessage(checkResult));
            result.put("data", buildHealthAlertData(checkResult));
        } catch (Exception e) {
            log.error("手动触发A股实时链路健康巡检失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "巡检失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发宏观快讯实时回扫
     */
    @GetMapping("/push/a/macro-realtime")
    public Map<String, Object> pushMacroRealtimeAlerts() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发宏观快讯实时回扫");
            MacroRealtimePushScanResult scanResult = macroRealtimePushService.scanAndPushRecentEvents();
            result.put("success", true);
            result.put("message", resolveMacroRealtimeMessage(scanResult));
            result.put("data", scanResult);
        } catch (Exception e) {
            log.error("手动触发宏观快讯实时回扫失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "回扫失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发美股夜报（盘前预警）- 晚上20:30
     * 数据范围：过去24小时（昨晚20:30到今晚20:30）
     */
    @GetMapping("/push/us/evening")
    public Map<String, Object> pushUSEveningReport() {
        Map<String, Object> result = new HashMap<>();
        if (!morningReportScheduler.isUsPushEnabled()) {
            result.put("success", true);
            result.put("message", "美股推送当前已关闭，已跳过");
            result.put("dataRange", "过去24小时（昨晚20:30到今晚20:30）");
            return result;
        }
        try {
            log.info("手动触发美股夜报（盘前预警）");
            morningReportScheduler.pushUSEveningReportManually();
            result.put("success", true);
            result.put("message", "美股夜报（盘前预警）推送成功");
            result.put("dataRange", "过去24小时（昨晚20:30到今晚20:30）");
        } catch (Exception e) {
            log.error("手动触发美股夜报失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 一键触发所有推送任务（测试用）
     */
    @GetMapping("/push/all")
    public Map<String, Object> pushAllReports() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> details = new HashMap<>();
        
        try {
            log.info("手动触发所有推送任务");
            
            // 美股早报（隔夜复盘）
            if (!morningReportScheduler.isUsPushEnabled()) {
                details.put("usMorning", Map.of("success", true, "message", "美股推送已关闭，已跳过"));
            } else {
                try {
                    morningReportScheduler.pushUSMorningReportManually();
                    details.put("usMorning", Map.of("success", true, "message", "美股早报推送成功"));
                } catch (Exception e) {
                    details.put("usMorning", Map.of("success", false, "message", e.getMessage()));
                }
            }
            
            // A股盘前早报
            try {
                morningReportScheduler.pushAMorningReportManually();
                details.put("aMorning", Map.of("success", true, "message", "A股盘前早报推送成功"));
            } catch (Exception e) {
                details.put("aMorning", Map.of("success", false, "message", e.getMessage()));
            }
            
            // A股盘后复盘
            try {
                morningReportScheduler.pushAEveningReportManually();
                details.put("aEvening", Map.of("success", true, "message", "A股盘后复盘推送成功"));
            } catch (Exception e) {
                details.put("aEvening", Map.of("success", false, "message", e.getMessage()));
            }

            // A股实时链路健康巡检
            try {
                AStockPushHealthCheckResult checkResult = aStockPushHealthAlertService.inspectAndPushIfNeeded();
                details.put("aRealtimeHealth", Map.of(
                        "success", true,
                        "message", resolveHealthAlertMessage(checkResult),
                        "alertTriggered", checkResult != null && checkResult.isAlertTriggered(),
                        "pushed", checkResult != null && checkResult.isPushed()
                ));
            } catch (Exception e) {
                details.put("aRealtimeHealth", Map.of("success", false, "message", e.getMessage()));
            }

            // 宏观快讯实时回扫
            try {
                MacroRealtimePushScanResult scanResult = macroRealtimePushService.scanAndPushRecentEvents();
                details.put("macroRealtime", Map.of(
                        "success", true,
                        "message", resolveMacroRealtimeMessage(scanResult),
                        "pushedCount", scanResult != null ? scanResult.getPushedCount() : 0,
                        "scannedCount", scanResult != null ? scanResult.getScannedCount() : 0
                ));
            } catch (Exception e) {
                details.put("macroRealtime", Map.of("success", false, "message", e.getMessage()));
            }

            // 美股夜报
            if (!morningReportScheduler.isUsPushEnabled()) {
                details.put("usEvening", Map.of("success", true, "message", "美股推送已关闭，已跳过"));
            } else {
                try {
                    morningReportScheduler.pushUSEveningReportManually();
                    details.put("usEvening", Map.of("success", true, "message", "美股夜报推送成功"));
                } catch (Exception e) {
                    details.put("usEvening", Map.of("success", false, "message", e.getMessage()));
                }
            }
            
            result.put("success", true);
            result.put("message", "所有推送任务执行完成");
            result.put("details", details);
        } catch (Exception e) {
            log.error("批量推送失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "批量推送失败: " + e.getMessage());
        }
        return result;
    }

    private String resolveHealthAlertMessage(AStockPushHealthCheckResult checkResult) {
        if (checkResult == null) {
            return "A股实时链路健康巡检已执行";
        }
        if (!checkResult.isAlertTriggered()) {
            return "A股实时链路健康，未触发健康告警";
        }
        if (checkResult.isPushed()) {
            return "A股实时链路健康告警已推送";
        }
        return "A股实时链路健康告警已触发，但未重复推送";
    }

    private Map<String, Object> buildHealthAlertData(AStockPushHealthCheckResult checkResult) {
        if (checkResult == null) {
            return Map.of();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("windowStart", checkResult.getWindowStart());
        data.put("windowEnd", checkResult.getWindowEnd());
        data.put("marketState", checkResult.getMarketState());
        data.put("snapshotHealth", checkResult.getSnapshotHealth());
        data.put("snapshotSource", checkResult.getSnapshotSource());
        data.put("snapshotConsecutiveFailureCount", checkResult.getSnapshotConsecutiveFailureCount());
        data.put("snapshotBreadthSampleSize", checkResult.getSnapshotBreadthSampleSize());
        data.put("snapshotFallback", checkResult.isSnapshotFallback());
        data.put("snapshotStale", checkResult.isSnapshotStale());
        data.put("snapshotCapturedAt", checkResult.getSnapshotCapturedAt());
        data.put("snapshotLastSuccessAt", checkResult.getSnapshotLastSuccessAt());
        data.put("snapshotLastFailureAt", checkResult.getSnapshotLastFailureAt());
        data.put("snapshotNextRetryAt", checkResult.getSnapshotNextRetryAt());
        data.put("snapshotLastFailureReason", checkResult.getSnapshotLastFailureReason());
        data.put("highSignalNoticeCount", checkResult.getHighSignalNoticeCount());
        data.put("hardRiskNoticeCount", checkResult.getHardRiskNoticeCount());
        data.put("decisionCount", checkResult.getDecisionCount());
        data.put("sentCount", checkResult.getSentCount());
        data.put("skippedCount", checkResult.getSkippedCount());
        data.put("failedCount", checkResult.getFailedCount());
        data.put("riskSentCount", checkResult.getRiskSentCount());
        data.put("macroRiskEventCount", checkResult.getMacroRiskEventCount());
        data.put("macroOpportunityEventCount", checkResult.getMacroOpportunityEventCount());
        data.put("macroSentCount", checkResult.getMacroSentCount());
        data.put("macroRiskSentCount", checkResult.getMacroRiskSentCount());
        data.put("alertTriggered", checkResult.isAlertTriggered());
        data.put("pushed", checkResult.isPushed());
        data.put("alertSummary", checkResult.getAlertSummary());
        data.put("alertReason", checkResult.getAlertReason());
        data.put("sampleNoticeTitles", checkResult.getSampleNoticeTitles());
        data.put("sampleDecisionReasons", checkResult.getSampleDecisionReasons());
        return data;
    }

    private String resolveMacroRealtimeMessage(MacroRealtimePushScanResult scanResult) {
        if (scanResult == null) {
            return "宏观快讯实时回扫已执行";
        }
        return scanResult.getPushedCount() != null && scanResult.getPushedCount() > 0
                ? "宏观快讯实时推送已执行"
                : "宏观快讯实时回扫完成，未命中可推送事件";
    }
}
