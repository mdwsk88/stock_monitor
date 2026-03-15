package com.dawei.controller;

import com.dawei.scheduler.MorningReportScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName ReportPushControllerTest
 * @Author dawei
 * @Description ReportPushController 单元测试
 **/
@ExtendWith(MockitoExtension.class)
class ReportPushControllerTest {

    @Mock
    private MorningReportScheduler morningReportScheduler;

    @InjectMocks
    private ReportPushController reportPushController;

    @Test
    @DisplayName("测试手动触发美股早报（隔夜复盘）- 成功")
    void testPushUSMorningReportSuccess() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);

        Map<String, Object> result = reportPushController.pushUSMorningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("美股早报（隔夜复盘）推送成功", result.get("message"));
        assertEquals("过去12小时（昨晚20:00到今早8:00）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushUSMorningReportManually();
    }

    @Test
    @DisplayName("测试手动触发美股早报（隔夜复盘）- 失败")
    void testPushUSMorningReportFailure() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);
        doThrow(new RuntimeException("数据库连接失败")).when(morningReportScheduler).pushUSMorningReportManually();

        Map<String, Object> result = reportPushController.pushUSMorningReport();

        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("推送失败"));
        assertTrue(((String) result.get("message")).contains("数据库连接失败"));
        verify(morningReportScheduler, times(1)).pushUSMorningReportManually();
    }

    @Test
    @DisplayName("测试手动触发美股早报（隔夜复盘）- 已关闭")
    void testPushUSMorningReportDisabled() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(false);

        Map<String, Object> result = reportPushController.pushUSMorningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("美股推送当前已关闭，已跳过", result.get("message"));
        verify(morningReportScheduler, never()).pushUSMorningReportManually();
    }

    @Test
    @DisplayName("测试手动触发A股盘前早报 - 成功")
    void testPushAMorningReportSuccess() {
        Map<String, Object> result = reportPushController.pushAMorningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("A股盘前早报推送成功", result.get("message"));
        assertEquals("过去24小时（昨天8:30到今天8:30）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushAMorningReportManually();
    }

    @Test
    @DisplayName("测试手动触发A股盘前早报 - 失败")
    void testPushAMorningReportFailure() {
        doThrow(new RuntimeException("API限流")).when(morningReportScheduler).pushAMorningReportManually();

        Map<String, Object> result = reportPushController.pushAMorningReport();

        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("API限流"));
        verify(morningReportScheduler, times(1)).pushAMorningReportManually();
    }

    @Test
    @DisplayName("测试手动触发A股盘后复盘 - 成功")
    void testPushAEveningReportSuccess() {
        Map<String, Object> result = reportPushController.pushAEveningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("A股盘后复盘推送成功", result.get("message"));
        assertEquals("当天9:00到15:00（过去6小时）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushAEveningReportManually();
    }

    @Test
    @DisplayName("测试手动触发A股盘后复盘 - 失败")
    void testPushAEveningReportFailure() {
        doThrow(new RuntimeException("网络超时")).when(morningReportScheduler).pushAEveningReportManually();

        Map<String, Object> result = reportPushController.pushAEveningReport();

        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("网络超时"));
        verify(morningReportScheduler, times(1)).pushAEveningReportManually();
    }

    @Test
    @DisplayName("测试手动触发美股夜报（盘前预警）- 成功")
    void testPushUSEveningReportSuccess() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);

        Map<String, Object> result = reportPushController.pushUSEveningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("美股夜报（盘前预警）推送成功", result.get("message"));
        assertEquals("过去24小时（昨晚20:30到今晚20:30）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushUSEveningReportManually();
    }

    @Test
    @DisplayName("测试手动触发美股夜报（盘前预警）- 失败")
    void testPushUSEveningReportFailure() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);
        doThrow(new RuntimeException("AI服务异常")).when(morningReportScheduler).pushUSEveningReportManually();

        Map<String, Object> result = reportPushController.pushUSEveningReport();

        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("AI服务异常"));
        verify(morningReportScheduler, times(1)).pushUSEveningReportManually();
    }

    @Test
    @DisplayName("测试手动触发美股夜报（盘前预警）- 已关闭")
    void testPushUSEveningReportDisabled() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(false);

        Map<String, Object> result = reportPushController.pushUSEveningReport();

        assertTrue((Boolean) result.get("success"));
        assertEquals("美股推送当前已关闭，已跳过", result.get("message"));
        verify(morningReportScheduler, never()).pushUSEveningReportManually();
    }

    @Test
    @DisplayName("测试一键触发所有推送任务 - 全部成功")
    void testPushAllReportsAllSuccess() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);

        Map<String, Object> result = reportPushController.pushAllReports();

        assertTrue((Boolean) result.get("success"));
        assertEquals("所有推送任务执行完成", result.get("message"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertNotNull(details);
        assertEquals(4, details.size());

        verify(morningReportScheduler, times(1)).pushUSMorningReportManually();
        verify(morningReportScheduler, times(1)).pushAMorningReportManually();
        verify(morningReportScheduler, times(1)).pushAEveningReportManually();
        verify(morningReportScheduler, times(1)).pushUSEveningReportManually();
    }

    @Test
    @DisplayName("测试一键触发所有推送任务 - 部分失败")
    void testPushAllReportsPartialFailure() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(true);
        doThrow(new RuntimeException("美股早报失败")).when(morningReportScheduler).pushUSMorningReportManually();
        doThrow(new RuntimeException("A股复盘失败")).when(morningReportScheduler).pushAEveningReportManually();

        Map<String, Object> result = reportPushController.pushAllReports();

        assertTrue((Boolean) result.get("success"));
        assertEquals("所有推送任务执行完成", result.get("message"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertNotNull(details);

        verify(morningReportScheduler, times(1)).pushUSMorningReportManually();
        verify(morningReportScheduler, times(1)).pushAMorningReportManually();
        verify(morningReportScheduler, times(1)).pushAEveningReportManually();
        verify(morningReportScheduler, times(1)).pushUSEveningReportManually();
    }

    @Test
    @DisplayName("测试一键触发所有推送任务 - 美股已关闭")
    void testPushAllReportsUsDisabled() {
        when(morningReportScheduler.isUsPushEnabled()).thenReturn(false);

        Map<String, Object> result = reportPushController.pushAllReports();

        assertTrue((Boolean) result.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertNotNull(details);
        assertEquals(4, details.size());
        assertTrue(details.get("usMorning").toString().contains("已关闭"));
        assertTrue(details.get("usEvening").toString().contains("已关闭"));
        verify(morningReportScheduler, never()).pushUSMorningReportManually();
        verify(morningReportScheduler, never()).pushUSEveningReportManually();
        verify(morningReportScheduler, times(1)).pushAMorningReportManually();
        verify(morningReportScheduler, times(1)).pushAEveningReportManually();
    }
}
