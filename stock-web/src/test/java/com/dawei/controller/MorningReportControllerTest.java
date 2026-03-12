package com.dawei.controller;

import com.dawei.scheduler.MorningReportScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName MorningReportControllerTest
 * @Author dawei
 * @Description MorningReportController 单元测试
 **/
@ExtendWith(MockitoExtension.class)
class MorningReportControllerTest {

    @Mock
    private MorningReportScheduler morningReportScheduler;

    @InjectMocks
    private MorningReportController morningReportController;

    @BeforeEach
    void setUp() {
        // MockitoExtension 会自动处理注入
    }

    @Test
    @DisplayName("测试手动触发美股早报（隔夜复盘）- 成功")
    void testPushUSMorningReport_Success() {
        // 执行
        Map<String, Object> result = morningReportController.pushUSMorningReport();

        // 验证
        assertTrue((Boolean) result.get("success"));
        assertEquals("美股早报（隔夜复盘）推送成功", result.get("message"));
        assertEquals("过去12小时（昨晚20:00到今早8:00）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushUSMorningReport();
    }

    @Test
    @DisplayName("测试手动触发美股早报（隔夜复盘）- 失败")
    void testPushUSMorningReport_Failure() {
        // 模拟异常
        doThrow(new RuntimeException("数据库连接失败")).when(morningReportScheduler).pushUSMorningReport();

        // 执行
        Map<String, Object> result = morningReportController.pushUSMorningReport();

        // 验证
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("推送失败"));
        assertTrue(((String) result.get("message")).contains("数据库连接失败"));
        verify(morningReportScheduler, times(1)).pushUSMorningReport();
    }

    @Test
    @DisplayName("测试手动触发A股盘前早报 - 成功")
    void testPushAMorningReport_Success() {
        // 执行
        Map<String, Object> result = morningReportController.pushAMorningReport();

        // 验证
        assertTrue((Boolean) result.get("success"));
        assertEquals("A股盘前早报推送成功", result.get("message"));
        assertEquals("过去24小时（昨天8:30到今天8:30）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushAMorningReport();
    }

    @Test
    @DisplayName("测试手动触发A股盘前早报 - 失败")
    void testPushAMorningReport_Failure() {
        // 模拟异常
        doThrow(new RuntimeException("API限流")).when(morningReportScheduler).pushAMorningReport();

        // 执行
        Map<String, Object> result = morningReportController.pushAMorningReport();

        // 验证
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("API限流"));
        verify(morningReportScheduler, times(1)).pushAMorningReport();
    }

    @Test
    @DisplayName("测试手动触发A股盘后复盘 - 成功")
    void testPushAEveningReport_Success() {
        // 执行
        Map<String, Object> result = morningReportController.pushAEveningReport();

        // 验证
        assertTrue((Boolean) result.get("success"));
        assertEquals("A股盘后复盘推送成功", result.get("message"));
        assertEquals("当天9:00到15:00（过去6小时）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushAEveningReport();
    }

    @Test
    @DisplayName("测试手动触发A股盘后复盘 - 失败")
    void testPushAEveningReport_Failure() {
        // 模拟异常
        doThrow(new RuntimeException("网络超时")).when(morningReportScheduler).pushAEveningReport();

        // 执行
        Map<String, Object> result = morningReportController.pushAEveningReport();

        // 验证
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("网络超时"));
        verify(morningReportScheduler, times(1)).pushAEveningReport();
    }

    @Test
    @DisplayName("测试手动触发美股夜报（盘前预警）- 成功")
    void testPushUSEveningReport_Success() {
        // 执行
        Map<String, Object> result = morningReportController.pushUSEveningReport();

        // 验证
        assertTrue((Boolean) result.get("success"));
        assertEquals("美股夜报（盘前预警）推送成功", result.get("message"));
        assertEquals("过去24小时（昨晚20:30到今晚20:30）", result.get("dataRange"));
        verify(morningReportScheduler, times(1)).pushUSEveningReport();
    }

    @Test
    @DisplayName("测试手动触发美股夜报（盘前预警）- 失败")
    void testPushUSEveningReport_Failure() {
        // 模拟异常
        doThrow(new RuntimeException("AI服务异常")).when(morningReportScheduler).pushUSEveningReport();

        // 执行
        Map<String, Object> result = morningReportController.pushUSEveningReport();

        // 验证
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("AI服务异常"));
        verify(morningReportScheduler, times(1)).pushUSEveningReport();
    }

    @Test
    @DisplayName("测试一键触发所有推送任务 - 全部成功")
    void testPushAllReports_AllSuccess() {
        // 执行
        Map<String, Object> result = morningReportController.pushAllReports();

        // 验证
        assertTrue((Boolean) result.get("success"));
        assertEquals("所有推送任务执行完成", result.get("message"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertNotNull(details);
        assertEquals(4, details.size());
        
        // 验证每个任务都被调用
        verify(morningReportScheduler, times(1)).pushUSMorningReport();
        verify(morningReportScheduler, times(1)).pushAMorningReport();
        verify(morningReportScheduler, times(1)).pushAEveningReport();
        verify(morningReportScheduler, times(1)).pushUSEveningReport();
    }

    @Test
    @DisplayName("测试一键触发所有推送任务 - 部分失败")
    void testPushAllReports_PartialFailure() {
        // 模拟部分任务失败
        doThrow(new RuntimeException("美股早报失败")).when(morningReportScheduler).pushUSMorningReport();
        doThrow(new RuntimeException("A股复盘失败")).when(morningReportScheduler).pushAEveningReport();

        // 执行
        Map<String, Object> result = morningReportController.pushAllReports();

        // 验证
        assertTrue((Boolean) result.get("success")); // 整体仍然返回成功，因为其他任务完成了
        assertEquals("所有推送任务执行完成", result.get("message"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertNotNull(details);
        
        // 验证所有任务都被尝试调用
        verify(morningReportScheduler, times(1)).pushUSMorningReport();
        verify(morningReportScheduler, times(1)).pushAMorningReport();
        verify(morningReportScheduler, times(1)).pushAEveningReport();
        verify(morningReportScheduler, times(1)).pushUSEveningReport();
    }
}
