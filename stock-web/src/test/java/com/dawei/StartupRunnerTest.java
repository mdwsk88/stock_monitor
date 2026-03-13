package com.dawei;

import com.dawei.service.RssService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private StartupRunner startupRunner;

    @Test
    void testRunExecutesBothTasks() throws Exception {
        log.info("========== 测试启动时执行两个低频任务 ==========");

        // 执行启动任务
        startupRunner.run();

        // 验证美股低频任务被执行
        verify(rssService, times(1)).displayRss();

        // 验证A股低频任务被执行
        verify(rssService, times(1)).fetchAndSaveAStockNotices();

        log.info("✅ 启动任务执行验证通过 - 两个低频任务都被调用");
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

        log.info("✅ 异常隔离验证通过 - 美股失败不影响A股任务执行");
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

        log.info("✅ 异常隔离验证通过 - A股失败不影响美股任务执行");
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
        org.mockito.InOrder inOrder = inOrder(rssService);

        startupRunner.run();

        // 验证先执行美股，后执行A股
        inOrder.verify(rssService).displayRss();
        inOrder.verify(rssService).fetchAndSaveAStockNotices();

        log.info("✅ 执行顺序验证通过 - 先美股后A股");
    }
}
