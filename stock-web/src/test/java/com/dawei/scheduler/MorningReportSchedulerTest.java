package com.dawei.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MorningReportSchedulerTest {

    @Test
    void shouldAllowMorningWindowBoundary() {
        assertTrue(MorningReportScheduler.isWithinExecutionWindow(
                LocalDateTime.of(2026, 3, 16, 8, 0),
                LocalTime.of(8, 0),
                LocalTime.of(9, 40)));
        assertTrue(MorningReportScheduler.isWithinExecutionWindow(
                LocalDateTime.of(2026, 3, 16, 9, 40),
                LocalTime.of(8, 0),
                LocalTime.of(9, 40)));
    }

    @Test
    void shouldRejectExpiredMorningWindow() {
        assertFalse(MorningReportScheduler.isWithinExecutionWindow(
                LocalDateTime.of(2026, 3, 16, 14, 24),
                LocalTime.of(8, 0),
                LocalTime.of(9, 40)));
    }

    @Test
    void shouldAllowEveningWindowButRejectTooLateRun() {
        assertTrue(MorningReportScheduler.isWithinExecutionWindow(
                LocalDateTime.of(2026, 3, 16, 15, 30),
                LocalTime.of(15, 0),
                LocalTime.of(16, 30)));
        assertFalse(MorningReportScheduler.isWithinExecutionWindow(
                LocalDateTime.of(2026, 3, 16, 18, 0),
                LocalTime.of(15, 0),
                LocalTime.of(16, 30)));
    }

    @Test
    void shouldSkipScheduledReportsWhenSchedulerDisabled() {
        MorningReportScheduler scheduler = new MorningReportScheduler(mock(com.dawei.service.StockRankService.class),
                mock(com.dawei.service.AReportFusionService.class),
                mock(com.dawei.service.AISummaryService.class),
                mock(com.dawei.utils.WeComApi.class));
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        scheduler.pushAMorningReport();
        scheduler.pushAEveningReport();
        scheduler.pushUSMorningReport();
        scheduler.pushUSEveningReport();

        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "stockRankService"));
        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "aiSummaryService"));
        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "weComApi"));
    }
}
