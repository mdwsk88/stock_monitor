package com.dawei.controller;

import com.dawei.entity.AStockPushHealthCheckResult;
import com.dawei.entity.MacroRealtimePushScanResult;
import com.dawei.scheduler.MorningReportScheduler;
import com.dawei.service.impl.AStockIntradayDemoPushService;
import com.dawei.service.impl.AStockPushHealthAlertService;
import com.dawei.service.impl.MacroRealtimePushService;
import com.dawei.utils.PushLanguageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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
    private final AStockIntradayDemoPushService aStockIntradayDemoPushService;
    private final MacroRealtimePushService macroRealtimePushService;
    private final PushLanguageService pushLanguageService;

    public ReportPushController(MorningReportScheduler morningReportScheduler,
                                AStockPushHealthAlertService aStockPushHealthAlertService,
                                AStockIntradayDemoPushService aStockIntradayDemoPushService,
                                MacroRealtimePushService macroRealtimePushService,
                                PushLanguageService pushLanguageService) {
        this.morningReportScheduler = morningReportScheduler;
        this.aStockPushHealthAlertService = aStockPushHealthAlertService;
        this.aStockIntradayDemoPushService = aStockIntradayDemoPushService;
        this.macroRealtimePushService = macroRealtimePushService;
        this.pushLanguageService = pushLanguageService;
    }

    /**
     * 手动触发美股早报（隔夜复盘）- 早上7:30
     * 数据范围：过去12小时（昨晚20:00到今早8:00）
     */
    @GetMapping("/push/us/morning")
    public Map<String, Object> pushUSMorningReport(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            if (!morningReportScheduler.isUsPushEnabled()) {
                result.put("success", true);
                result.put("message", pushLanguageService.text("美股推送当前已关闭，已跳过", "US stock push is disabled and was skipped"));
                result.put("dataRange", pushLanguageService.text("过去12小时（昨晚20:00到今早8:00）", "Past 12 hours (20:00 last night to 08:00 today)"));
                return result;
            }
            try {
                log.info("手动触发美股早报（隔夜复盘）");
                morningReportScheduler.pushUSMorningReportManually();
                result.put("success", true);
                result.put("message", pushLanguageService.text("美股早报（隔夜复盘）推送成功", "US overnight recap pushed successfully"));
                result.put("dataRange", pushLanguageService.text("过去12小时（昨晚20:00到今早8:00）", "Past 12 hours (20:00 last night to 08:00 today)"));
            } catch (Exception e) {
                log.error("手动触发美股早报失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股盘前早报 - 早上8:30
     * 数据范围：过去24小时（昨天8:30到今天8:30）
     */
    @GetMapping("/push/a/morning")
    public Map<String, Object> pushAMorningReport(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            try {
                log.info("手动触发A股盘前早报");
                morningReportScheduler.pushAMorningReportManually();
                result.put("success", true);
                result.put("message", pushLanguageService.text("A股盘前早报推送成功", "A-stock pre-market report pushed successfully"));
                result.put("dataRange", pushLanguageService.text("过去24小时（昨天8:30到今天8:30）", "Past 24 hours (08:30 yesterday to 08:30 today)"));
            } catch (Exception e) {
                log.error("手动触发A股盘前早报失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股盘后复盘 - 下午15:30
     * 数据范围：当天9:00到15:00（过去6小时）
     */
    @GetMapping("/push/a/evening")
    public Map<String, Object> pushAEveningReport(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            try {
                log.info("手动触发A股盘后复盘");
                morningReportScheduler.pushAEveningReportManually();
                result.put("success", true);
                result.put("message", pushLanguageService.text("A股盘后复盘推送成功", "A-stock post-close decode pushed successfully"));
                result.put("dataRange", pushLanguageService.text("当天9:00到当前执行时间", "Today 09:00 to the current execution time"));
            } catch (Exception e) {
                log.error("手动触发A股盘后复盘失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股盘后风险速递 - 晚上18:30
     * 数据范围：当天15:00到当前执行时间
     */
    @GetMapping("/push/a/post-close-risk")
    public Map<String, Object> pushAPostCloseRiskDigest(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            try {
                log.info("手动触发A股盘后风险速递");
                morningReportScheduler.pushAPostCloseRiskDigestManually();
                result.put("success", true);
                result.put("message", pushLanguageService.text("A股盘后风险速递推送成功", "A-stock post-close risk digest pushed successfully"));
                result.put("dataRange", pushLanguageService.text("当天15:00到当前执行时间", "Today 15:00 to the current execution time"));
            } catch (Exception e) {
                log.error("手动触发A股盘后风险速递失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股盘中机会快讯演示（mock 数据，真实推送链路）
     */
    @GetMapping("/push/a/demo/intraday-opportunity")
    public Map<String, Object> pushAIntradayOpportunityDemo(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            try {
                log.info("手动触发A股盘中机会快讯演示");
                AStockIntradayDemoPushService.DemoPushResult demoResult = aStockIntradayDemoPushService.pushOpportunityDemo();
                result.put("success", true);
                result.put("message", pushLanguageService.text("A股盘中机会快讯（演示）推送成功", "A-stock intraday opportunity demo pushed successfully"));
                result.put("dataRange", pushLanguageService.text("演示数据（mock intraday feed）", "Demo data (mock intraday feed)"));
                result.put("data", demoResult);
            } catch (Exception e) {
                log.error("手动触发A股盘中机会快讯演示失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股盘中风险快讯演示（mock 数据，真实推送链路）
     */
    @GetMapping("/push/a/demo/intraday-risk")
    public Map<String, Object> pushAIntradayRiskDemo(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            try {
                log.info("手动触发A股盘中风险快讯演示");
                AStockIntradayDemoPushService.DemoPushResult demoResult = aStockIntradayDemoPushService.pushRiskDemo();
                result.put("success", true);
                result.put("message", pushLanguageService.text("A股盘中风险快讯（演示）推送成功", "A-stock intraday risk demo pushed successfully"));
                result.put("dataRange", pushLanguageService.text("演示数据（mock intraday feed）", "Demo data (mock intraday feed)"));
                result.put("data", demoResult);
            } catch (Exception e) {
                log.error("手动触发A股盘中风险快讯演示失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发A股实时链路健康巡检
     */
    @GetMapping("/push/a/realtime-health")
    public Map<String, Object> pushARealtimeHealthAlert(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
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
                result.put("message", pushLanguageService.text("巡检失败: ", "Health check failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发宏观快讯实时回扫
     */
    @GetMapping("/push/a/macro-realtime")
    public Map<String, Object> pushMacroRealtimeAlerts(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
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
                result.put("message", pushLanguageService.text("回扫失败: ", "Realtime macro scan failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 手动触发美股夜报（盘前预警）- 晚上20:30
     * 数据范围：过去24小时（昨晚20:30到今晚20:30）
     */
    @GetMapping("/push/us/evening")
    public Map<String, Object> pushUSEveningReport(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            if (!morningReportScheduler.isUsPushEnabled()) {
                result.put("success", true);
                result.put("message", pushLanguageService.text("美股推送当前已关闭，已跳过", "US stock push is disabled and was skipped"));
                result.put("dataRange", pushLanguageService.text("过去24小时（昨晚20:30到今晚20:30）", "Past 24 hours (20:30 last night to 20:30 tonight)"));
                return result;
            }
            try {
                log.info("手动触发美股夜报（盘前预警）");
                morningReportScheduler.pushUSEveningReportManually();
                result.put("success", true);
                result.put("message", pushLanguageService.text("美股夜报（盘前预警）推送成功", "US night radar pushed successfully"));
                result.put("dataRange", pushLanguageService.text("过去24小时（昨晚20:30到今晚20:30）", "Past 24 hours (20:30 last night to 20:30 tonight)"));
            } catch (Exception e) {
                log.error("手动触发美股夜报失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("推送失败: ", "Push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    /**
     * 一键触发所有推送任务（测试用）
     */
    @GetMapping("/push/all")
    public Map<String, Object> pushAllReports(@RequestParam(value = "lang", required = false) String language) {
        return withRequestLanguage(language, () -> {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> details = new HashMap<>();

            try {
                log.info("手动触发所有推送任务");

                if (!morningReportScheduler.isUsPushEnabled()) {
                    details.put("usMorning", Map.of("success", true, "message",
                            pushLanguageService.text("美股推送已关闭，已跳过", "US stock push is disabled and was skipped")));
                } else {
                    try {
                        morningReportScheduler.pushUSMorningReportManually();
                        details.put("usMorning", Map.of("success", true, "message",
                                pushLanguageService.text("美股早报推送成功", "US overnight recap pushed successfully")));
                    } catch (Exception e) {
                        details.put("usMorning", Map.of("success", false, "message", e.getMessage()));
                    }
                }

                try {
                    morningReportScheduler.pushAMorningReportManually();
                    details.put("aMorning", Map.of("success", true, "message",
                            pushLanguageService.text("A股盘前早报推送成功", "A-stock pre-market report pushed successfully")));
                } catch (Exception e) {
                    details.put("aMorning", Map.of("success", false, "message", e.getMessage()));
                }

                try {
                    morningReportScheduler.pushAEveningReportManually();
                    details.put("aEvening", Map.of("success", true, "message",
                            pushLanguageService.text("A股盘后复盘推送成功", "A-stock post-close decode pushed successfully")));
                } catch (Exception e) {
                    details.put("aEvening", Map.of("success", false, "message", e.getMessage()));
                }

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

                if (!morningReportScheduler.isUsPushEnabled()) {
                    details.put("usEvening", Map.of("success", true, "message",
                            pushLanguageService.text("美股推送已关闭，已跳过", "US stock push is disabled and was skipped")));
                } else {
                    try {
                        morningReportScheduler.pushUSEveningReportManually();
                        details.put("usEvening", Map.of("success", true, "message",
                                pushLanguageService.text("美股夜报推送成功", "US night radar pushed successfully")));
                    } catch (Exception e) {
                        details.put("usEvening", Map.of("success", false, "message", e.getMessage()));
                    }
                }

                result.put("success", true);
                result.put("message", pushLanguageService.text("所有推送任务执行完成", "All push flows finished"));
                result.put("details", details);
            } catch (Exception e) {
                log.error("批量推送失败: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("message", pushLanguageService.text("批量推送失败: ", "Batch push failed: ") + e.getMessage());
            }
            return result;
        });
    }

    private Map<String, Object> withRequestLanguage(String language, Supplier<Map<String, Object>> action) {
        return pushLanguageService.withLanguage(language, () -> {
            Map<String, Object> result = action.get();
            result.put("language", pushLanguageService.currentLanguage());
            return result;
        });
    }

    private String resolveHealthAlertMessage(AStockPushHealthCheckResult checkResult) {
        if (checkResult == null) {
            return pushLanguageService.text("A股实时链路健康巡检已执行", "A-stock realtime health check executed");
        }
        if (!checkResult.isAlertTriggered()) {
            return pushLanguageService.text("A股实时链路健康，未触发健康告警", "A-stock realtime pipeline is healthy; no health alert was triggered");
        }
        return pushLanguageService.text("A股实时链路发现异常，结果已保留，不再推送企业微信", "A-stock realtime pipeline found anomalies; the result was preserved and no WeCom push was sent");
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
            return pushLanguageService.text("宏观快讯实时回扫已执行", "Realtime macro scan executed");
        }
        return scanResult.getPushedCount() != null && scanResult.getPushedCount() > 0
                ? pushLanguageService.text("宏观快讯实时推送已执行", "Realtime macro push executed")
                : pushLanguageService.text("宏观快讯实时回扫完成，未命中可推送事件", "Realtime macro scan finished with no pushable events");
    }
}
