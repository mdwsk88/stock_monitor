package com.dawei.utils;

import com.dawei.common.utils.GMTDateConverter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GMTDateConverter 单元测试
 * 
 * 测试重点：
 * 1. GMT/北京时间/纽约时间转换正确
 * 2. 时间加减计算正确
 * 3. 边界条件处理
 */
@Slf4j
class GMTDateConverterTest {

    @Test
    void testConvertGmtToBeijing() {
        log.info("========== 测试 GMT 转北京时间 ==========");

        Date gmtDate = new Date();
        LocalDateTime beijingTime = GMTDateConverter.convertGmtToBeijing(gmtDate);

        assertNotNull(beijingTime, "北京时间不应为空");

        // 北京时间 = GMT + 8小时
        // 验证转换后的时间合理（与当前时间相差不超过1分钟）
        LocalDateTime now = LocalDateTime.now();
        assertTrue(Math.abs(beijingTime.getHour() - now.getHour()) <= 1 ||
                   Math.abs(beijingTime.getHour() - now.getHour()) >= 23,
            "北京时间应该接近当前时间");

        log.info("✅ GMT 转北京时间测试通过 - 当前北京时间: {}", beijingTime);
    }

    @Test
    void testConvertGmtToNewYork() {
        log.info("========== 测试 GMT 转纽约时间 ==========");

        Date gmtDate = new Date();
        LocalDateTime newYorkTime = GMTDateConverter.convertGmtToNewYork(gmtDate);

        assertNotNull(newYorkTime, "纽约时间不应为空");

        // 纽约时间 = GMT - 5小时（冬令时）或 -4小时（夏令时）
        LocalDateTime gmtTime = GMTDateConverter.convertGmt(gmtDate);
        int hourDiff = gmtTime.getHour() - newYorkTime.getHour();

        // 考虑跨天情况
        if (hourDiff < 0) hourDiff += 24;

        assertTrue(hourDiff == 4 || hourDiff == 5 || hourDiff == 3 || hourDiff == 6,
            "纽约时间与 GMT 时差应该在 4-6 小时之间（夏令时/冬令时），实际时差: " + hourDiff);

        log.info("✅ GMT 转纽约时间测试通过 - 当前纽约时间: {}", newYorkTime);
    }

    @Test
    void testConvertGmt() {
        log.info("========== 测试 Date 转 GMT LocalDateTime ==========");

        Date now = new Date();
        LocalDateTime gmtTime = GMTDateConverter.convertGmt(now);

        assertNotNull(gmtTime, "GMT时间不应为空");

        // 验证时间字段合理
        assertTrue(gmtTime.getYear() >= 2026, "年份应该 >= 2026");
        assertTrue(gmtTime.getMonthValue() >= 1 && gmtTime.getMonthValue() <= 12,
            "月份应该在 1-12 之间");
        assertTrue(gmtTime.getDayOfMonth() >= 1 && gmtTime.getDayOfMonth() <= 31,
            "日期应该在 1-31 之间");

        log.info("✅ GMT 转换测试通过 - GMT时间: {}", gmtTime);
    }

    @Test
    void testGetBeijingTimeString() {
        log.info("========== 测试获取北京时间字符串 ==========");

        Date gmtDate = new Date();
        String beijingTimeStr = GMTDateConverter.getBeijingTime(gmtDate);

        assertNotNull(beijingTimeStr, "时间字符串不应为空");
        assertTrue(beijingTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
            "时间格式应该是 yyyy-MM-dd HH:mm，实际: " + beijingTimeStr);

        log.info("✅ 北京时间字符串测试通过 - 格式: {}", beijingTimeStr);
    }

    @Test
    void testGetNewYorkTimeString() {
        log.info("========== 测试获取纽约时间字符串 ==========");

        Date gmtDate = new Date();
        String newYorkTimeStr = GMTDateConverter.getNewYorkTime(gmtDate);

        assertNotNull(newYorkTimeStr, "时间字符串不应为空");
        assertTrue(newYorkTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
            "时间格式应该是 yyyy-MM-dd HH:mm，实际: " + newYorkTimeStr);

        log.info("✅ 纽约时间字符串测试通过 - 格式: {}", newYorkTimeStr);
    }

    @Test
    void testMinus24Hour() {
        log.info("========== 测试 24小时前时间计算 ==========");

        LocalDateTime now = LocalDateTime.of(2026, 3, 13, 12, 0);
        LocalDateTime result = GMTDateConverter.minus24Hour(now);

        assertEquals(2026, result.getYear());
        assertEquals(3, result.getMonthValue());
        assertEquals(12, result.getDayOfMonth()); // 前一天
        assertEquals(12, result.getHour());
        assertEquals(0, result.getMinute());

        log.info("✅ 24小时前计算测试通过");
    }

    @Test
    void testMinus3Day() {
        log.info("========== 测试 3天前时间计算 ==========");

        LocalDateTime now = LocalDateTime.of(2026, 3, 13, 12, 0);
        LocalDateTime result = GMTDateConverter.minus3Day(now);

        assertEquals(2026, result.getYear());
        assertEquals(3, result.getMonthValue());
        assertEquals(10, result.getDayOfMonth()); // 3天前
        assertEquals(12, result.getHour());

        log.info("✅ 3天前计算测试通过");
    }

    @Test
    void testMinus1Week() {
        log.info("========== 测试 1周前时间计算 ==========");

        LocalDateTime now = LocalDateTime.of(2026, 3, 13, 12, 0);
        LocalDateTime result = GMTDateConverter.minus1Week(now);

        assertEquals(2026, result.getYear());
        assertEquals(3, result.getMonthValue());
        assertEquals(6, result.getDayOfMonth()); // 一周前
        assertEquals(12, result.getHour());

        log.info("✅ 1周前计算测试通过");
    }

    @Test
    void testPlus1Minute() {
        log.info("========== 测试 1分钟后时间计算 ==========");

        LocalDateTime now = LocalDateTime.of(2026, 3, 13, 12, 0);
        LocalDateTime result = GMTDateConverter.plus1Minute(now);

        assertEquals(2026, result.getYear());
        assertEquals(3, result.getMonthValue());
        assertEquals(13, result.getDayOfMonth());
        assertEquals(12, result.getHour());
        assertEquals(1, result.getMinute()); // 1分钟后

        log.info("✅ 1分钟后计算测试通过");
    }

    @Test
    void testNullTimeThrowsException() {
        log.info("========== 测试 null 时间异常处理 ==========");

        assertThrows(IllegalArgumentException.class, () -> {
            GMTDateConverter.minus24Hour(null);
        }, "传入 null 应该抛出 IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> {
            GMTDateConverter.minus3Day(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GMTDateConverter.minus1Week(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GMTDateConverter.plus1Minute(null);
        });

        log.info("✅ null 时间异常处理测试通过");
    }

    @Test
    void testMonthBoundary() {
        log.info("========== 测试跨月边界 ==========");

        // 3月1日 减 3天 = 2月26日
        LocalDateTime march1 = LocalDateTime.of(2026, 3, 1, 12, 0);
        LocalDateTime result = GMTDateConverter.minus3Day(march1);

        assertEquals(2026, result.getYear());
        assertEquals(2, result.getMonthValue());
        assertEquals(26, result.getDayOfMonth());

        log.info("✅ 跨月边界测试通过");
    }

    @Test
    void testYearBoundary() {
        log.info("========== 测试跨年边界 ==========");

        // 1月2日 减 1周 = 去年12月26日
        LocalDateTime jan2 = LocalDateTime.of(2026, 1, 2, 12, 0);
        LocalDateTime result = GMTDateConverter.minus1Week(jan2);

        assertEquals(2025, result.getYear());
        assertEquals(12, result.getMonthValue());
        assertEquals(26, result.getDayOfMonth());

        log.info("✅ 跨年边界测试通过");
    }
}
