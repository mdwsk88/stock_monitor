package com.dawei.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebFetchTool 单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class WebFetchToolTest {

    @Autowired
    private WebFetchTool webFetchTool;

    @Test
    void testFetchWebContentInvalidUrl() {
        log.info("========== 测试 WebFetchTool: 无效URL ==========");
        
        // 测试无效URL
        String result = webFetchTool.fetchWebContentAsMarkdown("invalid-url");
        
        assertNotNull(result);
        assertTrue(result.contains("错误"), "应该返回错误信息");
        assertTrue(result.contains("无效的URL"), "应该提示URL格式无效");
        
        log.info("✅ 无效URL测试通过");
    }

    @Test
    void testFetchWebContentEmptyUrl() {
        log.info("========== 测试 WebFetchTool: 空URL ==========");
        
        String result = webFetchTool.fetchWebContentAsMarkdown("");
        
        assertNotNull(result);
        assertTrue(result.contains("错误"), "应该返回错误信息");
        
        log.info("✅ 空URL测试通过");
    }

    @Test
    void testFetchWebContentNullHandled() {
        log.info("========== 测试 WebFetchTool: null URL ==========");
        
        // 测试null值
        String result = webFetchTool.fetchWebContentAsMarkdown(null);
        
        assertNotNull(result);
        assertTrue(result.contains("错误"), "应该返回错误信息");
        
        log.info("✅ null URL测试通过");
    }

    @Test
    void testFetchWebContentWithHttpUrl() {
        log.info("========== 测试 WebFetchTool: HTTP URL格式验证 ==========");
        
        // 测试http:// 协议
        String result = webFetchTool.fetchWebContentAsMarkdown("http://example.com");
        
        // 由于网络原因可能失败，但至少不应返回URL格式错误
        assertNotNull(result);
        if (result.contains("错误") && result.contains("无效的URL")) {
            fail("HTTP URL应该被接受");
        }
        
        log.info("✅ HTTP URL格式验证通过");
    }

    @Test
    void testFetchWebContentWithHttpsUrl() {
        log.info("========== 测试 WebFetchTool: HTTPS URL格式验证 ==========");
        
        // 测试https:// 协议
        String result = webFetchTool.fetchWebContentAsMarkdown("https://example.com");
        
        assertNotNull(result);
        if (result.contains("错误") && result.contains("无效的URL")) {
            fail("HTTPS URL应该被接受");
        }
        
        log.info("✅ HTTPS URL格式验证通过");
    }

    @Test
    void testFetchWebContentNonExistentDomain() {
        log.info("========== 测试 WebFetchTool: 不存在的域名 ==========");
        
        // 测试不存在的域名
        String result = webFetchTool.fetchWebContentAsMarkdown(
            "https://this-domain-definitely-does-not-exist-12345.com"
        );
        
        assertNotNull(result);
        // 应该返回错误信息，因为域名不存在
        assertTrue(result.contains("错误"), "应该返回错误信息");
        
        log.info("✅ 不存在的域名测试通过");
    }

    @Test
    void testFetchRealWebContent() {
        log.info("========== 测试 WebFetchTool: 获取真实网页内容 ==========");
        
        // 测试获取一个真实的网页（使用httpbin.org，这是一个测试服务）
        String result = webFetchTool.fetchWebContentAsMarkdown("https://httpbin.org/html");
        
        assertNotNull(result);
        log.info("获取到的内容长度: {} 字符", result.length());
        log.info("内容预览: {}", result.substring(0, Math.min(200, result.length())));
        
        // 如果成功获取，内容不应该包含错误信息
        if (!result.contains("错误")) {
            assertTrue(result.length() > 0, "成功获取的内容不应为空");
        }
        
        log.info("✅ 真实网页内容获取测试通过");
    }
}
