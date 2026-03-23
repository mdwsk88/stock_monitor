package com.dawei.scheduler;

import com.dawei.service.impl.MacroRealtimePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 宏观快讯实时回扫。
 */
@Slf4j
@Component
public class MacroRealtimePushScheduler {

    @Value("${stock.runtime.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    private final MacroRealtimePushService macroRealtimePushService;

    public MacroRealtimePushScheduler(MacroRealtimePushService macroRealtimePushService) {
        this.macroRealtimePushService = macroRealtimePushService;
    }

    @Scheduled(cron = "0 8/10 9-15 * * MON-FRI")
    public void scanRecentMacroEvents() {
        if (skipScheduledRuntime("宏观实时推送回扫")) {
            return;
        }
        macroRealtimePushService.scanAndPushRecentEvents();
    }

    private boolean skipScheduledRuntime(String jobName) {
        if (schedulerEnabled) {
            return false;
        }
        log.info("【{}】定时任务总开关已关闭，跳过执行", jobName);
        return true;
    }
}
