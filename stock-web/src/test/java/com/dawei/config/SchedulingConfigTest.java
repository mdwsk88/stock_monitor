package com.dawei.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingConfigTest {

    @Test
    void taskSchedulerShouldUseDedicatedPool() {
        SchedulingConfig config = new SchedulingConfig();

        ThreadPoolTaskScheduler scheduler = config.taskScheduler();
        scheduler.initialize();
        try {
            assertEquals(4, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
            assertTrue(scheduler.getThreadNamePrefix().startsWith("scheduler-"));
        } finally {
            scheduler.shutdown();
        }
    }
}
