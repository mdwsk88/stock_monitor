package com.dawei.scheduler;

import com.dawei.utils.PushLanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
        scheduler.pushAPostCloseRiskDigest();
        scheduler.pushUSMorningReport();
        scheduler.pushUSEveningReport();

        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "stockRankService"));
        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "aiSummaryService"));
        verifyNoInteractions(ReflectionTestUtils.getField(scheduler, "weComApi"));
    }

    @Test
    void shouldBuildEnglishNoDataMessageWhenLanguageSwitchOn() {
        MorningReportScheduler scheduler = new MorningReportScheduler(
                mock(com.dawei.service.StockRankService.class),
                mock(com.dawei.service.AReportFusionService.class),
                mock(com.dawei.service.AISummaryService.class),
                mock(com.dawei.utils.WeComApi.class),
                new PushLanguageService("en"));

        String message = ReflectionTestUtils.invokeMethod(scheduler, "buildNoDataMessage", "美股", "2026-03-16");

        assertTrue(message.contains("AI Pre-Market Alert Radar"));
        assertTrue(message.contains("No notable US stock alerts were detected"));
        assertTrue(message.contains("US Stock Analyst"));
        assertTrue(message.contains("Disclaimer"));
    }

    @Test
    void shouldBuildEnglishPostCloseDigestWithoutChineseHeadline() {
        MorningReportScheduler scheduler = new MorningReportScheduler(
                mock(com.dawei.service.StockRankService.class),
                mock(com.dawei.service.AReportFusionService.class),
                mock(com.dawei.service.AISummaryService.class),
                mock(com.dawei.utils.WeComApi.class),
                new PushLanguageService("en"));

        com.dawei.entity.AStockRss stock = new com.dawei.entity.AStockRss();
        stock.setStockCode("600001");
        stock.setStockName("示例股份");
        stock.setTitle("示例股份：收到监管处罚决定书");
        stock.setEventType("监管处罚");
        stock.setSignalSide("利空");

        com.dawei.entity.StockAlertDTO<com.dawei.entity.AStockRss> riskAlert =
                new com.dawei.entity.StockAlertDTO<>(stock, 3, 92, 2, "利空");

        com.dawei.entity.AReportFusionContext context = new com.dawei.entity.AReportFusionContext();
        context.setRiskAlerts(List.of(riskAlert));

        String message = ReflectionTestUtils.invokeMethod(
                scheduler,
                "buildAPostCloseRiskDigestMessage",
                "2026-04-03",
                context
        );

        assertTrue(message.contains("A-Stock Post-Close Risk Digest"));
        assertTrue(message.contains("Risk Priority"));
        assertTrue(message.contains("Regulatory Penalty"));
        assertTrue(message.contains("This is a high-priority bearish signal"));
        assertTrue(message.contains("Notice Count 3"));
        assertFalse(message.contains("收到监管处罚决定书"));
        assertFalse(message.contains("风险优先级"));
    }
}
