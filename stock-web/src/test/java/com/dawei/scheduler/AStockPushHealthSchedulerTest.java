package com.dawei.scheduler;

import com.dawei.service.impl.AStockPushHealthAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AStockPushHealthSchedulerTest {

    @Test
    void shouldSkipWhenSchedulerDisabled() {
        AStockPushHealthAlertService alertService = mock(AStockPushHealthAlertService.class);
        AStockPushHealthScheduler scheduler = new AStockPushHealthScheduler(alertService);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        scheduler.inspectRealtimeHealth();
        scheduler.inspectCloseRealtimeHealth();

        verifyNoInteractions(alertService);
    }

    @Test
    void shouldRunWhenSchedulerEnabled() {
        AStockPushHealthAlertService alertService = mock(AStockPushHealthAlertService.class);
        AStockPushHealthScheduler scheduler = new AStockPushHealthScheduler(alertService);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);

        scheduler.inspectRealtimeHealth();

        verify(alertService).inspectAndPushIfNeeded();
    }
}
