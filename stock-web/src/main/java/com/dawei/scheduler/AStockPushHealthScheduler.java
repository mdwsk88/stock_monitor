package com.dawei.scheduler;

import com.dawei.service.impl.AStockPushHealthAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A股实时推送健康巡检定时任务。
 */
@Slf4j
@Component
public class AStockPushHealthScheduler {

    @Value("${stock.runtime.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    private final AStockPushHealthAlertService aStockPushHealthAlertService;

    public AStockPushHealthScheduler(AStockPushHealthAlertService aStockPushHealthAlertService) {
        this.aStockPushHealthAlertService = aStockPushHealthAlertService;
    }

    @Scheduled(cron = "0 10/15 9-14 * * MON-FRI")
    public void inspectRealtimeHealth() {
        if (skipScheduledRuntime("A股实时链路健康巡检")) {
            return;
        }
        aStockPushHealthAlertService.inspectAndPushIfNeeded();
    }

    @Scheduled(cron = "0 5,20 15 * * MON-FRI")
    public void inspectCloseRealtimeHealth() {
        if (skipScheduledRuntime("A股收盘实时链路健康巡检")) {
            return;
        }
        aStockPushHealthAlertService.inspectAndPushIfNeeded();
    }

    private boolean skipScheduledRuntime(String jobName) {
        if (schedulerEnabled) {
            return false;
        }
        log.info("【{}】定时任务总开关已关闭，跳过执行", jobName);
        return true;
    }
}
