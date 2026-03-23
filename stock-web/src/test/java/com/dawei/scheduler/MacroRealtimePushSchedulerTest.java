package com.dawei.scheduler;

import com.dawei.service.impl.MacroRealtimePushService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MacroRealtimePushSchedulerTest {

    @Test
    void shouldSkipWhenSchedulerDisabled() {
        MacroRealtimePushService service = mock(MacroRealtimePushService.class);
        MacroRealtimePushScheduler scheduler = new MacroRealtimePushScheduler(service);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        scheduler.scanRecentMacroEvents();

        verifyNoInteractions(service);
    }

    @Test
    void shouldRunWhenSchedulerEnabled() {
        MacroRealtimePushService service = mock(MacroRealtimePushService.class);
        MacroRealtimePushScheduler scheduler = new MacroRealtimePushScheduler(service);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);

        scheduler.scanRecentMacroEvents();

        verify(service).scanAndPushRecentEvents();
    }
}
