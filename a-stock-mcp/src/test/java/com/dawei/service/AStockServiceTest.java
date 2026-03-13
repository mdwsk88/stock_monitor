package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AStockService 单元测试
 * 
 * ⚠️ 注意：本测试需要完整的数据库环境配置
 * 暂时禁用，待配置 test profile 后启用
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("需要完整的数据库环境配置")
@Slf4j
class AStockServiceTest {

    @Autowired
    private AStockService aStockService;

    @Test
    void testQueryStock() {
        log.info("========== 测试 queryStock - 光云科技(688365) ==========");
        List<AStockRss> result = aStockService.queryStock("688365");
        
        assertNotNull(result);
        assertEquals(5, result.size(), "光云科技应该有5条记录");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertEquals("688365", stock.getStockCode());
            assertEquals("光云科技", stock.getStockName());
        });
        
        log.info("✅ queryStock 测试通过");
    }

    @Test
    void testQueryStockByName() {
        log.info("========== 测试 queryStockByName - 光云科技 ==========");
        List<AStockRss> result = aStockService.queryStockByName("光云");
        
        assertNotNull(result);
        assertTrue(result.size() > 0, "应该能查询到包含'光云'的股票");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertTrue(stock.getStockName().contains("光云"));
        });
        
        log.info("✅ queryStockByName 测试通过");
    }

    @Test
    void testQueryStockBetweenDate() {
        log.info("========== 测试 queryStockBetweenDate - 光云科技 ==========");
        List<AStockRss> result = aStockService.queryStockBetweenDate(
                "688365", 
                "2024-01-15 00:00:00", 
                "2024-01-18 23:59:59"
        );
        
        assertNotNull(result);
        assertEquals(4, result.size(), "光云科技在指定日期范围内应该有4条记录");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertEquals("688365", stock.getStockCode());
        });
        
        log.info("✅ queryStockBetweenDate 测试通过");
    }

    @Test
    void testQueryStockCountsBetweenDate() {
        log.info("========== 测试 queryStockCountsBetweenDate ==========");
        List<StockCounts> result = aStockService.queryStockCountsBetweenDate(
                3, 
                "2024-01-15 00:00:00", 
                "2024-01-31 23:59:59"
        );
        
        assertNotNull(result);
        assertTrue(result.size() >= 1, "应该有至少1只股票出现次数>=3次");
        
        result.forEach(count -> {
            log.info("股票统计: {}({}) - {}次", count.getStockName(), count.getStockCode(), count.getOccurCounts());
            assertNotNull(count.getStockCode());
            assertNotNull(count.getOccurCounts());
        });
        
        // 验证光云科技出现5次
        boolean foundGuangyun = result.stream()
                .anyMatch(c -> "688365".equals(c.getStockCode()) && "5".equals(c.getOccurCounts()));
        assertTrue(foundGuangyun, "光云科技应该出现5次");
        
        log.info("✅ queryStockCountsBetweenDate 测试通过");
    }

    @Test
    void testQueryStockByTitleKeywords() {
        log.info("========== 测试 queryStockByTitleKeywords ==========");
        List<AStockRss> result = aStockService.queryStockByTitleKeywords(Arrays.asList("业绩", "产品"));
        
        assertNotNull(result);
        assertTrue(result.size() > 0, "应该能查询到包含'业绩'或'产品'的标题");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertTrue(
                stock.getTitle().contains("业绩") || stock.getTitle().contains("产品"),
                "标题应该包含关键词"
            );
        });
        
        log.info("✅ queryStockByTitleKeywords 测试通过");
    }

    @Test
    void testQueryStockByNameKeywords() {
        log.info("========== 测试 queryStockByNameKeywords ==========");
        List<AStockRss> result = aStockService.queryStockByNameKeywords(Arrays.asList("光云", "平安"));
        
        assertNotNull(result);
        assertTrue(result.size() > 0, "应该能查询到包含'光云'或'平安'的股票名称");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertTrue(
                stock.getStockName().contains("光云") || stock.getStockName().contains("平安"),
                "股票名称应该包含关键词"
            );
        });
        
        log.info("✅ queryStockByNameKeywords 测试通过");
    }

    @Test
    void testQueryStockNotFound() {
        log.info("========== 测试 queryStock - 不存在的股票 ==========");
        List<AStockRss> result = aStockService.queryStock("999999");
        
        assertNotNull(result);
        assertEquals(0, result.size(), "不存在的股票应该返回空列表");
        
        log.info("✅ queryStock - 不存在的股票 测试通过");
    }

    @Test
    void testQueryGuangyunFullName() {
        log.info("========== 测试 queryStockByName - 光云科技全名 ==========");
        List<AStockRss> result = aStockService.queryStockByName("光云科技");
        
        assertNotNull(result);
        assertEquals(5, result.size(), "光云科技全名查询应该返回5条记录");
        
        result.forEach(stock -> {
            log.info("查询结果: {}", stock);
            assertEquals("688365", stock.getStockCode());
            assertEquals("光云科技", stock.getStockName());
        });
        
        log.info("✅ queryStockByName - 光云科技全名 测试通过");
    }
}
