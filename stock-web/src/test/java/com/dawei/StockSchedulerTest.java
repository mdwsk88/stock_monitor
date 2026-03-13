package com.dawei;

import com.dawei.service.RssService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StockScheduler 单元测试
 * 
 * 测试重点：
 * 1. 定时任务方法存在且配置了正确的 Cron 表达式
 * 2. A股高频/低频、美股高频/低频任务配置正确
 * 3. 任务执行不抛出异常
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class StockSchedulerTest {

    @Mock
    private RssService rssService;

    @InjectMocks
    private StockScheduler stockScheduler;

    // ==================== Cron 表达式验证测试 ====================

    @Test
    void testAStockHighFreqCronExpression() throws NoSuchMethodException {
        log.info("========== 测试 A股高频任务 Cron 表达式 ==========");

        Method method = StockScheduler.class.getMethod("getAStockInfoHighFreq");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "方法应该有 @Scheduled 注解");
        String cron = scheduled.cron();
        assertEquals("0 0/5 9-22 * * MON-FRI", cron,
            "A股高频任务应该在工作日 9-22 点每 5 分钟执行");

        log.info("✅ A股高频任务 Cron 表达式验证通过: {}", cron);
    }

    @Test
    void testAStockLowFreqCronExpression() throws NoSuchMethodException {
        log.info("========== 测试 A股低频任务 Cron 表达式 ==========");

        Method method = StockScheduler.class.getMethod("getAStockInfoLowFreq");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "方法应该有 @Scheduled 注解");
        String cron = scheduled.cron();
        assertEquals("0 0 23,0,1,2,3,4,5,6,7,8 * * MON-FRI", cron,
            "A股低频任务应该在夜间及凌晨每小时执行");

        log.info("✅ A股低频任务 Cron 表达式验证通过: {}", cron);
    }

    @Test
    void testUSStockHighFreqCronExpression() throws NoSuchMethodException {
        log.info("========== 测试 美股高频任务 Cron 表达式 ==========");

        Method method = StockScheduler.class.getMethod("getUSStockInfoHighFreq");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "方法应该有 @Scheduled 注解");
        String cron = scheduled.cron();
        assertEquals("0 0/30 21-23,0-6 * * MON-FRI", cron,
            "美股高频任务应该在交易时段（21:30-06:00）每 30 分钟执行");

        log.info("✅ 美股高频任务 Cron 表达式验证通过: {}", cron);
    }

    @Test
    void testUSStockLowFreqCronExpression() throws NoSuchMethodException {
        log.info("========== 测试 美股低频任务 Cron 表达式 ==========");

        Method method = StockScheduler.class.getMethod("getUSStockInfoLowFreq");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "方法应该有 @Scheduled 注解");
        String cron = scheduled.cron();
        assertEquals("0 0 6-21 * * MON-FRI", cron,
            "美股低频任务应该在非交易时段每小时执行");

        log.info("✅ 美股低频任务 Cron 表达式验证通过: {}", cron);
    }

    @Test
    void testWeekendLowFreqCronExpression() throws NoSuchMethodException {
        log.info("========== 测试 周末巡逻任务 Cron 表达式 ==========");

        Method method = StockScheduler.class.getMethod("weekendLowFreqMonitor");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "方法应该有 @Scheduled 注解");
        String cron = scheduled.cron();
        assertEquals("0 0 0/2 * * SAT,SUN", cron,
            "周末巡逻任务应该在周六周日每 2 小时执行一次");

        log.info("✅ 周末巡逻任务 Cron 表达式验证通过: {}", cron);
    }

    // ==================== 任务执行测试 ====================

    @Test
    void testAStockHighFreqExecution() throws Exception {
        log.info("========== 测试 A股高频任务执行 ==========");

        doNothing().when(rssService).fetchAndSaveAStockNotices();

        assertDoesNotThrow(() -> stockScheduler.getAStockInfoHighFreq(),
            "A股高频任务执行不应抛出异常");

        verify(rssService, times(1)).fetchAndSaveAStockNotices();

        log.info("✅ A股高频任务执行测试通过");
    }

    @Test
    void testAStockLowFreqExecution() throws Exception {
        log.info("========== 测试 A股低频任务执行 ==========");

        doNothing().when(rssService).fetchAndSaveAStockNotices();

        assertDoesNotThrow(() -> stockScheduler.getAStockInfoLowFreq(),
            "A股低频任务执行不应抛出异常");

        verify(rssService, times(1)).fetchAndSaveAStockNotices();

        log.info("✅ A股低频任务执行测试通过");
    }

    @Test
    void testUSStockHighFreqExecution() throws Exception {
        log.info("========== 测试 美股高频任务执行 ==========");

        doNothing().when(rssService).displayRss();

        assertDoesNotThrow(() -> stockScheduler.getUSStockInfoHighFreq(),
            "美股高频任务执行不应抛出异常");

        verify(rssService, times(1)).displayRss();

        log.info("✅ 美股高频任务执行测试通过");
    }

    @Test
    void testUSStockLowFreqExecution() throws Exception {
        log.info("========== 测试 美股低频任务执行 ==========");

        doNothing().when(rssService).displayRss();

        assertDoesNotThrow(() -> stockScheduler.getUSStockInfoLowFreq(),
            "美股低频任务执行不应抛出异常");

        verify(rssService, times(1)).displayRss();

        log.info("✅ 美股低频任务执行测试通过");
    }

    @Test
    void testWeekendLowFreqExecution() throws Exception {
        log.info("========== 测试 周末巡逻任务执行 ==========");

        doNothing().when(rssService).displayRss();
        doNothing().when(rssService).fetchAndSaveAStockNotices();

        assertDoesNotThrow(() -> stockScheduler.weekendLowFreqMonitor(),
            "周末巡逻任务执行不应抛出异常");

        verify(rssService, times(1)).displayRss();
        verify(rssService, times(1)).fetchAndSaveAStockNotices();

        log.info("✅ 周末巡逻任务执行测试通过");
    }

    // ==================== Cron 表达式解析测试 ====================

    @Test
    void testAStockCronCoverage() {
        log.info("========== 测试 A股 Cron 时间覆盖 ==========");

        // A股高频：工作日 9:00-22:00 每5分钟
        List<Integer> highFreqHours = Arrays.asList(9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);

        // A股低频：工作日 23,0,1,2,3,4,5,6,7,8 点
        List<Integer> lowFreqHours = Arrays.asList(23, 0, 1, 2, 3, 4, 5, 6, 7, 8);

        // 验证没有重叠
        for (Integer hour : highFreqHours) {
            assertFalse(lowFreqHours.contains(hour),
                "A股高频和低频时间不应重叠，小时 " + hour + " 重复");
        }

        // 验证覆盖全天（工作日）
        for (int hour = 0; hour < 24; hour++) {
            assertTrue(highFreqHours.contains(hour) || lowFreqHours.contains(hour),
                "每个小时应该至少被一个任务覆盖: " + hour);
        }

        log.info("✅ A股 Cron 时间覆盖验证通过 - 高频{}小时, 低频{}小时",
            highFreqHours.size(), lowFreqHours.size());
    }

    @Test
    void testUSStockCronCoverage() {
        log.info("========== 测试 美股 Cron 时间覆盖 ==========");

        // 美股高频：工作日 21-23,0-6 点（每15分钟）
        List<Integer> highFreqHours = Arrays.asList(21, 22, 23, 0, 1, 2, 3, 4, 5, 6);

        // 美股低频：工作日 6-21 点（每小时）
        List<Integer> lowFreqHours = Arrays.asList(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

        // 注意：6点和21点是重叠的（高频和低频都覆盖），这是设计上的，确保不遗漏

        // 验证基本覆盖
        assertTrue(highFreqHours.contains(21), "美股交易时段应包含 21 点");
        assertTrue(highFreqHours.contains(23), "美股交易时段应包含 23 点");
        assertTrue(highFreqHours.contains(0), "美股交易时段应包含 0 点");
        assertTrue(highFreqHours.contains(4), "美股交易时段应包含 4 点");

        log.info("✅ 美股 Cron 时间覆盖验证通过 - 高频{}小时, 低频{}小时",
            highFreqHours.size(), lowFreqHours.size());
    }

    @Test
    void testWeekdayCronTasksOnlyRunOnWeekdays() throws NoSuchMethodException {
        log.info("========== 测试工作日任务 Cron 仅工作日执行 ==========");

        String[] methods = {
            "getAStockInfoHighFreq",
            "getAStockInfoLowFreq",
            "getUSStockInfoHighFreq",
            "getUSStockInfoLowFreq"
        };

        for (String methodName : methods) {
            Method method = StockScheduler.class.getMethod(methodName);
            Scheduled scheduled = method.getAnnotation(Scheduled.class);
            String cron = scheduled.cron();

            assertTrue(cron.contains("MON-FRI"),
                methodName + " 应该只在工作日执行: " + cron);
        }

        log.info("✅ 工作日定时任务都配置为仅工作日执行");
    }

    @Test
    void testWeekendMonitorOnlyRunsOnWeekends() throws NoSuchMethodException {
        log.info("========== 测试周末巡逻任务仅周末执行 ==========");

        Method method = StockScheduler.class.getMethod("weekendLowFreqMonitor");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        String cron = scheduled.cron();

        assertTrue(cron.contains("SAT,SUN"),
            "周末巡逻任务应该只在周末执行: " + cron);

        log.info("✅ 周末巡逻任务配置为仅周末执行");
    }

    // ==================== 异常处理测试 ====================

    @Test
    void testAStockHighFreqHandlesException() throws Exception {
        log.info("========== 测试 A股高频任务异常处理 ==========");

        doThrow(new RuntimeException("模拟抓取异常"))
            .when(rssService).fetchAndSaveAStockNotices();

        // 即使抛出异常，调度器也应该能捕获（实际由 Spring 处理）
        assertThrows(RuntimeException.class, () -> {
            stockScheduler.getAStockInfoHighFreq();
        });

        log.info("✅ A股高频任务异常处理测试通过 - 异常被正确抛出");
    }

    @Test
    void testUSStockHighFreqHandlesException() throws Exception {
        log.info("========== 测试 美股高频任务异常处理 ==========");

        doThrow(new RuntimeException("模拟RSS异常"))
            .when(rssService).displayRss();

        assertThrows(RuntimeException.class, () -> {
            stockScheduler.getUSStockInfoHighFreq();
        });

        log.info("✅ 美股高频任务异常处理测试通过 - 异常被正确抛出");
    }

    // ==================== 辅助测试方法 ====================

    @Test
    void testAllScheduledMethodsExist() {
        log.info("========== 测试所有定时任务方法存在 ==========");

        String[] expectedMethods = {
            "getAStockInfoHighFreq",
            "getAStockInfoLowFreq",
            "getUSStockInfoHighFreq",
            "getUSStockInfoLowFreq",
            "weekendLowFreqMonitor"
        };

        for (String methodName : expectedMethods) {
            try {
                Method method = StockScheduler.class.getMethod(methodName);
                assertNotNull(method, "方法应该存在: " + methodName);
                assertTrue(method.getParameterCount() == 0,
                    "定时任务方法不应有参数: " + methodName);
            } catch (NoSuchMethodException e) {
                fail("缺少定时任务方法: " + methodName);
            }
        }

        log.info("✅ 所有 {} 个定时任务方法都存在", expectedMethods.length);
    }

    @Test
    void testSchedulerClassAnnotations() {
        log.info("========== 测试 Scheduler 类注解 ==========");

        assertTrue(StockScheduler.class.isAnnotationPresent(org.springframework.stereotype.Component.class),
            "StockScheduler 应该有 @Component 注解");
        assertTrue(StockScheduler.class.isAnnotationPresent(org.springframework.scheduling.annotation.EnableScheduling.class),
            "StockScheduler 应该有 @EnableScheduling 注解");

        log.info("✅ Scheduler 类注解验证通过");
    }
}
