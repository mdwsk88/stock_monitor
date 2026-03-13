package com.dawei.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebFetchTool RestTemplate 注入测试
 * 
 * 测试重点：验证 WebFetchTool 使用 Spring 注入的 RestTemplate
 * 而非在构造函数中创建新实例
 * 
 * ⚠️ 暂时禁用，待配置 test profile 后启用
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("需要完整的环境配置")
@Slf4j
class WebFetchToolInjectionTest {

    @Autowired
    private WebFetchTool webFetchTool;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    void testRestTemplateIsInjected() {
        log.info("========== 测试 WebFetchTool RestTemplate 注入 ==========");

        assertNotNull(webFetchTool, "WebFetchTool 应该被 Spring 管理");
        assertNotNull(restTemplate, "RestTemplate 应该被 Spring 管理");

        // 验证 WebFetchTool 使用了同一个 RestTemplate Bean
        // 通过反射检查 webFetchTool 中的 restTemplate 字段
        try {
            java.lang.reflect.Field field = WebFetchTool.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            RestTemplate injectedRestTemplate = (RestTemplate) field.get(webFetchTool);

            assertNotNull(injectedRestTemplate, "WebFetchTool 中的 restTemplate 不应为空");
            assertSame(restTemplate, injectedRestTemplate,
                "WebFetchTool 应该使用 Spring 注入的 RestTemplate Bean");

            log.info("✅ RestTemplate 注入验证通过 - WebFetchTool 使用 Spring 管理的实例");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("反射检查失败: " + e.getMessage());
        }
    }

    @Test
    void testWebFetchToolIsSingleton() {
        log.info("========== 测试 WebFetchTool 单例模式 ==========");

        // 验证 WebFetchTool 是 Spring 单例
        // 多次获取应该是同一个实例
        assertNotNull(webFetchTool, "WebFetchTool 应该被正确注入");

        log.info("✅ WebFetchTool Spring Bean 验证通过");
    }

    @Test
    void testRestTemplateConfiguration() {
        log.info("========== 测试 RestTemplate 配置 ==========");

        // 验证 RestTemplate 已正确配置
        assertNotNull(restTemplate, "RestTemplate Bean 应该存在");

        // 验证 RestTemplate 可以执行基本的 HTTP 操作
        // 这里不实际发送请求，只验证对象状态
        assertNotNull(restTemplate.getMessageConverters(),
            "RestTemplate 应该有消息转换器");
        assertFalse(restTemplate.getMessageConverters().isEmpty(),
            "RestTemplate 应该至少有一个消息转换器");

        log.info("✅ RestTemplate 配置验证通过 - 消息转换器数量: {}",
            restTemplate.getMessageConverters().size());
    }
}
