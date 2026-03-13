package com.dawei.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebFetchTool RestTemplate 注入单元测试
 * 
 * 测试重点：验证 WebFetchTool 使用 Spring 注入的 RestTemplate
 * 而非在构造函数中创建新实例
 */
@Slf4j
class WebFetchToolInjectionTest {

    @Test
    void testWebFetchToolUsesResourceAnnotation() {
        log.info("========== 测试 WebFetchTool 使用 @Resource 注入 ==========");

        // 验证 WebFetchTool 类使用了 @Resource 注解来注入 RestTemplate
        try {
            java.lang.reflect.Field field = WebFetchTool.class.getDeclaredField("restTemplate");
            
            // 检查字段上是否有 @Resource 注解
            boolean hasResourceAnnotation = field.isAnnotationPresent(jakarta.annotation.Resource.class);
            assertTrue(hasResourceAnnotation, 
                "WebFetchTool.restTemplate 字段应该使用 @Resource 注解进行依赖注入");

            log.info("✅ WebFetchTool 正确使用 @Resource 注入 RestTemplate");
        } catch (NoSuchFieldException e) {
            fail("WebFetchTool 应该有 restTemplate 字段: " + e.getMessage());
        }
    }

    @Test
    void testWebFetchToolIsComponent() {
        log.info("========== 测试 WebFetchTool 是 Spring 组件 ==========");

        // 验证类上有 @Component 注解
        boolean hasComponent = WebFetchTool.class.isAnnotationPresent(org.springframework.stereotype.Component.class);
        assertTrue(hasComponent, "WebFetchTool 应该有 @Component 注解，被 Spring 管理");

        log.info("✅ WebFetchTool 是 Spring 管理的组件");
    }

    @Test
    void testRestTemplateFieldType() {
        log.info("========== 测试 RestTemplate 字段类型 ==========");

        try {
            java.lang.reflect.Field field = WebFetchTool.class.getDeclaredField("restTemplate");
            
            // 验证字段类型是 RestTemplate
            assertEquals(RestTemplate.class, field.getType(), 
                "restTemplate 字段类型应该是 RestTemplate");

            // 验证字段不是 final（因为是注入的）
            assertFalse(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                "restTemplate 字段不应该声明为 final，以便 Spring 注入");

            log.info("✅ RestTemplate 字段类型和修饰符验证通过");
        } catch (NoSuchFieldException e) {
            fail("反射检查失败: " + e.getMessage());
        }
    }

    @Test
    void testNoNewRestTemplateInConstructor() {
        log.info("========== 测试构造函数中不创建 RestTemplate ==========");

        // 检查构造函数参数
        java.lang.reflect.Constructor<?>[] constructors = WebFetchTool.class.getDeclaredConstructors();
        
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            // 验证构造函数参数中不包含 RestTemplate（应该是无参构造）
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertNotEquals(RestTemplate.class, paramType,
                    "构造函数不应该接收 RestTemplate 参数，应该使用依赖注入");
            }
        }

        log.info("✅ 构造函数验证通过 - 使用依赖注入而非构造参数");
    }
}
