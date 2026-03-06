package com.dawei.controller;

import com.dawei.scheduler.MorningReportScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName MorningReportController
 * @Author dawei
 * @Version 1.0
 * @Description 盘前早报手动触发接口（用于测试）
 **/
@Slf4j
@RestController
@RequestMapping("/api/morning-report")
public class MorningReportController {

    private final MorningReportScheduler morningReportScheduler;

    public MorningReportController(MorningReportScheduler morningReportScheduler) {
        this.morningReportScheduler = morningReportScheduler;
    }

    /**
     * 手动触发美股盘前早报
     */
    @GetMapping("/push/us")
    public Map<String, Object> pushUSMorningReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发美股盘前早报");
            morningReportScheduler.pushUSMorningReport();
            result.put("success", true);
            result.put("message", "美股盘前早报推送成功");
        } catch (Exception e) {
            log.error("手动触发美股盘前早报失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发A股盘前早报
     */
    @GetMapping("/push/a")
    public Map<String, Object> pushAMorningReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("手动触发A股盘前早报");
            morningReportScheduler.pushAMorningReport();
            result.put("success", true);
            result.put("message", "A股盘前早报推送成功");
        } catch (Exception e) {
            log.error("手动触发A股盘前早报失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
        }
        return result;
    }
}
