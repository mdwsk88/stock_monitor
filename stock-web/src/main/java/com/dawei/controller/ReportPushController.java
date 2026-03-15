package com.dawei.controller;

import com.dawei.scheduler.MorningReportScheduler;
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

    public ReportPushController(MorningReportScheduler morningReportScheduler) {
        this.morningReportScheduler = morningReportScheduler;
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
            result.put("dataRange", "当天9:00到15:00（过去6小时）");
        } catch (Exception e) {
            log.error("手动触发A股盘后复盘失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
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
}
