package com.dawei.utils;

import com.dawei.common.utils.MD5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MD5 工具类单元测试
 * 
 * 测试重点：
 * 1. 字符串 MD5 计算正确
 * 2. 空值处理
 * 3. 相同输入产生相同输出
 * 4. 不同输入产生不同输出
 */
@Slf4j
class MD5Test {

    @Test
    void testMd5String() {
        log.info("========== 测试字符串 MD5 计算 ==========");

        String input = "Hello World";
        String hash = MD5.md5(input);

        assertNotNull(hash, "MD5 结果不应为空");
        assertEquals(32, hash.length(), "MD5 字符串长度应为 32");
        assertTrue(hash.matches("[a-f0-9]{32}"), "MD5 应该只包含小写十六进制字符");

        log.info("✅ 字符串 MD5 测试通过 - 输入: {}, 输出: {}", input, hash);
    }

    @Test
    void testMd5Consistency() {
        log.info("========== 测试 MD5 一致性 ==========");

        String input = "Test String";
        String hash1 = MD5.md5(input);
        String hash2 = MD5.md5(input);

        assertEquals(hash1, hash2, "相同输入应该产生相同的 MD5 值");

        log.info("✅ MD5 一致性测试通过");
    }

    @Test
    void testMd5DifferentInputs() {
        log.info("========== 测试不同输入产生不同 MD5 ==========");

        String hash1 = MD5.md5("Hello");
        String hash2 = MD5.md5("World");

        assertNotEquals(hash1, hash2, "不同输入应该产生不同的 MD5 值");

        log.info("✅ 不同输入 MD5 测试通过");
    }

    @Test
    void testMd5NullInput() {
        log.info("========== 测试 null 输入 ==========");

        String hash = MD5.md5((String) null);

        assertNull(hash, "null 输入应该返回 null");

        log.info("✅ null 输入测试通过");
    }

    @Test
    void testMd5EmptyString() {
        log.info("========== 测试空字符串 MD5 ==========");

        String hash = MD5.md5("");

        assertNotNull(hash, "空字符串的 MD5 不应为 null");
        assertEquals(32, hash.length(), "空字符串的 MD5 长度应为 32");

        // 空字符串的 MD5 是已知的: d41d8cd98f00b204e9800998ecf8427e
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash,
            "空字符串的 MD5 值应该是固定的");

        log.info("✅ 空字符串 MD5 测试通过 - 值: {}", hash);
    }

    @Test
    void testMd5Unicode() {
        log.info("========== 测试 Unicode 字符串 MD5 ==========");

        String input = "你好世界 🌍";
        String hash = MD5.md5(input);

        assertNotNull(hash, "Unicode 字符串的 MD5 不应为 null");
        assertEquals(32, hash.length(), "MD5 长度应为 32");

        // 验证一致性
        String hash2 = MD5.md5(input);
        assertEquals(hash, hash2, "Unicode 字符串 MD5 应该一致");

        log.info("✅ Unicode 字符串 MD5 测试通过 - 输入: {}, 输出: {}", input, hash);
    }

    @Test
    void testMd5LongString() {
        log.info("========== 测试长字符串 MD5 ==========");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Lorem ipsum dolor sit amet. ");
        }
        String input = sb.toString();

        String hash = MD5.md5(input);

        assertNotNull(hash, "长字符串的 MD5 不应为 null");
        assertEquals(32, hash.length(), "无论输入多长，MD5 长度始终为 32");

        log.info("✅ 长字符串 MD5 测试通过 - 输入长度: {}, MD5: {}", input.length(), hash);
    }

    @Test
    void testMd5SpecialCharacters() {
        log.info("========== 测试特殊字符 MD5 ==========");

        String[] inputs = {
            "!@#$%^&*()",
            "<script>alert('xss')</script>",
            "   ",  // 空格
            "\t\n\r",  // 控制字符
            "' OR '1'='1",  // SQL 注入尝试
        };

        for (String input : inputs) {
            String hash = MD5.md5(input);
            assertNotNull(hash, "特殊字符输入的 MD5 不应为 null");
            assertEquals(32, hash.length(), "MD5 长度应为 32");
            assertTrue(hash.matches("[a-f0-9]{32}"), "MD5 格式应该正确");
        }

        log.info("✅ 特殊字符 MD5 测试通过 - 测试了 {} 种特殊输入", inputs.length);
    }

    @Test
    void testMd5KnownValues() {
        log.info("========== 测试已知 MD5 值 ==========");

        // 一些已知的 MD5 测试向量
        assertEquals("5d41402abc4b2a76b9719d911017c592", MD5.md5("hello"));
        assertEquals("098f6bcd4621d373cade4e832627b4f6", MD5.md5("test"));
        assertEquals("e99a18c428cb38d5f260853678922e03", MD5.md5("abc123"));

        log.info("✅ 已知 MD5 值测试通过");
    }
}
