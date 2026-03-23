package com.dawei.scheduler;

import com.dawei.service.MarketStateService;
import com.dawei.service.impl.MarketPulsePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 盘中市场状态刷新与脉冲推送。
 */
@Slf4j
@Component
public class MarketPulseScheduler {

    @Value("${stock.runtime.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    private final MarketStateService marketStateService;
    private final MarketPulsePushService marketPulsePushService;

    public MarketPulseScheduler(MarketStateService marketStateService,
                                MarketPulsePushService marketPulsePushService) {
        this.marketStateService = marketStateService;
        this.marketPulsePushService = marketPulsePushService;
    }

    @Scheduled(cron = "0 55 8 * * MON-FRI")
    public void warmUpBeforeOpen() {
        if (skipScheduledRuntime("盘前市场状态预热")) {
            return;
        }
        marketStateService.refreshSnapshot();
    }

    @Scheduled(cron = "0 0/5 9-14 * * MON-FRI")
    public void refreshIntradayPulse() {
        if (skipScheduledRuntime("盘中市场状态脉冲")) {
            return;
        }
        marketPulsePushService.refreshAndPushIfNeeded();
    }

    @Scheduled(cron = "0 0,5 15 * * MON-FRI")
    public void refreshClosePulse() {
        if (skipScheduledRuntime("收盘市场状态脉冲")) {
            return;
        }
        marketPulsePushService.refreshAndPushIfNeeded();
    }

    private boolean skipScheduledRuntime(String jobName) {
        if (schedulerEnabled) {
            return false;
        }
        log.info("【{}】定时任务总开关已关闭，跳过执行", jobName);
        return true;
    }
}
