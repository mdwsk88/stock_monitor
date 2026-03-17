package com.dawei;

import com.dawei.service.RssService;
import com.dawei.service.MacroNewsService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StartupRunner 单元测试
 * 
 * 测试重点：
 * 1. 启动时执行美股低频任务
 * 2. 启动时执行A股低频任务
 * 3. 任务失败不影响其他任务执行
 * 4. CommandLineRunner 接口实现正确
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class StartupRunnerTest {

    @Mock
    private RssService rssService;

    @Mock
    private MacroNewsService macroNewsService;

    @InjectMocks
    private StartupRunner startupRunner;

    @Test
    void testRunExecutesBothTasks() throws Exception {
        log.info("========== 测试启动时执行三条初始化任务 ==========");

        // 执行启动任务
        startupRunner.run();

        // 验证美股低频任务被执行
        verify(rssService, times(1)).displayRss();

        // 验证A股低频任务被执行
        verify(rssService, times(1)).fetchAndSaveAStockNotices();

        // 验证宏观主题抓取被执行
        verify(macroNewsService, times(1)).fetchAndSaveMacroNews();

        log.info("✅ 启动任务执行验证通过 - 三条初始化任务都被调用");
    }

    @Test
    void testRunContinuesWhenUSDTaskFails() throws Exception {
        log.info("========== 测试美股任务失败时A股任务继续执行 ==========");

        // 模拟美股任务抛出异常
        doThrow(new RuntimeException("美股API异常"))
            .when(rssService).displayRss();

        // 执行启动任务 - 不应该抛出异常
        assertDoesNotThrow(() -> startupRunner.run());

        // 验证美股任务被调用
        verify(rssService, times(1)).displayRss();

        // 验证即使美股任务失败，A股任务仍然被执行
        verify(rssService, times(1)).fetchAndSaveAStockNotices();
        verify(macroNewsService, times(1)).fetchAndSaveMacroNews();

        log.info("✅ 异常隔离验证通过 - 美股失败不影响后续任务执行");
    }

    @Test
    void testRunContinuesWhenACNTaskFails() throws Exception {
        log.info("========== 测试A股任务失败时美股任务继续执行 ==========");

        // 模拟A股任务抛出异常
        doThrow(new RuntimeException("A股API异常"))
            .when(rssService).fetchAndSaveAStockNotices();

        // 执行启动任务 - 不应该抛出异常
        assertDoesNotThrow(() -> startupRunner.run());

        // 验证美股任务被调用
        verify(rssService, times(1)).displayRss();

        // 验证A股任务被调用（虽然失败了）
        verify(rssService, times(1)).fetchAndSaveAStockNotices();
        verify(macroNewsService, times(1)).fetchAndSaveMacroNews();

        log.info("✅ 异常隔离验证通过 - A股失败不影响宏观任务执行");
    }

    @Test
    void testRunContinuesWhenMacroTaskFails() throws Exception {
        log.info("========== 测试宏观任务失败时前序任务不受影响 ==========");

        doThrow(new RuntimeException("宏观源异常"))
                .when(macroNewsService).fetchAndSaveMacroNews();

        assertDoesNotThrow(() -> startupRunner.run());

        verify(rssService, times(1)).displayRss();
        verify(rssService, times(1)).fetchAndSaveAStockNotices();
        verify(macroNewsService, times(1)).fetchAndSaveMacroNews();

        log.info("✅ 异常隔离验证通过 - 宏观任务失败不影响前序任务");
    }

    @Test
    void testImplementsCommandLineRunner() {
        log.info("========== 测试实现 CommandLineRunner 接口 ==========");

        assertTrue(startupRunner instanceof org.springframework.boot.CommandLineRunner,
            "StartupRunner 应该实现 CommandLineRunner 接口");

        log.info("✅ 接口实现验证通过");
    }

    @Test
    void testIsSpringComponent() {
        log.info("========== 测试是 Spring 组件 ==========");

        assertTrue(StartupRunner.class.isAnnotationPresent(org.springframework.stereotype.Component.class),
            "StartupRunner 应该有 @Component 注解，被 Spring 管理");

        log.info("✅ Spring 组件验证通过");
    }

    @Test
    void testExecutionOrder() throws Exception {
        log.info("========== 测试任务执行顺序 ==========");

        // 创建 inOrder 验证器来验证调用顺序
        org.mockito.InOrder inOrder = inOrder(rssService, macroNewsService);

        startupRunner.run();

        // 验证先执行美股，后执行A股，最后执行宏观
        inOrder.verify(rssService).displayRss();
        inOrder.verify(rssService).fetchAndSaveAStockNotices();
        inOrder.verify(macroNewsService).fetchAndSaveMacroNews();

        log.info("✅ 执行顺序验证通过 - 先美股后A股再宏观");
    }

    @Test
    void testRunSkipsUSStartupWhenRuntimeDisabled() throws Exception {
        ReflectionTestUtils.setField(startupRunner, "usRuntimeEnabled", false);

        startupRunner.run();

        verify(rssService, never()).displayRss();
        verify(rssService, times(1)).fetchAndSaveAStockNotices();
        verify(macroNewsService, times(1)).fetchAndSaveMacroNews();
    }

    @Test
    void testRunSkipsAllStartupTasksWhenStartupDisabled() throws Exception {
        ReflectionTestUtils.setField(startupRunner, "startupEnabled", false);

        startupRunner.run();

        verifyNoInteractions(rssService);
        verifyNoInteractions(macroNewsService);
    }
}
