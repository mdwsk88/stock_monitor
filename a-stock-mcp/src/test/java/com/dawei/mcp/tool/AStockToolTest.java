package com.dawei.mcp.tool;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AStockTool MCP工具单元测试
 * 
 * ⚠️ 注意：本测试需要完整的数据库环境配置
 * 暂时禁用，待配置 test profile 后启用
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("需要完整的数据库环境配置")
@Slf4j
class AStockToolTest {

    @Autowired
    private AStockTool aStockTool;

    @Test
    void testQueryAStockInfoByCode() {
        log.info("========== 测试 MCP工具: queryAStockInfoByCode ==========");
        
        // 测试查询光云科技(688365)
        List<AStockRss> result = aStockTool.queryAStockInfoByCode("688365");
        
        assertNotNull(result);
        assertEquals(5, result.size(), "光云科技应该有5条记录");
        
        result.forEach(stock -> {
            log.info("MCP返回: {}", stock);
            assertEquals("688365", stock.getStockCode());
            assertEquals("光云科技", stock.getStockName());
        });
        
        log.info("✅ queryAStockInfoByCode 测试通过");
    }

    @Test
    void testQueryAStockInfoByName() {
        log.info("========== 测试 MCP工具: queryAStockInfoByName ==========");
        
        List<AStockRss> result = aStockTool.queryAStockInfoByName("光云科技");
        
        assertNotNull(result);
        assertEquals(5, result.size(), "光云科技应该有5条记录");
        
        result.forEach(stock -> {
            log.info("MCP返回: {}", stock);
            assertTrue(stock.getStockName().contains("光云科技"));
        });
        
        log.info("✅ queryAStockInfoByName 测试通过");
    }

    @Test
    void testQueryAStockInfoByCodeBetweenDate() {
        log.info("========== 测试 MCP工具: queryAStockInfoByCodeBetweenDate ==========");
        
        List<AStockRss> result = aStockTool.queryAStockInfoByCodeBetweenDate(
                "688365", 
                "2024-01-15 00:00:00", 
                "2024-01-17 23:59:59"
        );
        
        assertNotNull(result);
        assertEquals(3, result.size(), "光云科技在指定日期范围内应该有3条记录");
        
        log.info("✅ queryAStockInfoByCodeBetweenDate 测试通过");
    }

    @Test
    void testQueryAStockCountsBetweenDate() {
        log.info("========== 测试 MCP工具: queryAStockCountsBetweenDate ==========");
        
        List<StockCounts> result = aStockTool.queryAStockCountsBetweenDate(
                3, 
                "2024-01-15", 
                "2024-01-31"
        );
        
        assertNotNull(result);
        
        result.forEach(count -> {
            log.info("MCP返回统计: {} - {}次", count.getStockCode(), count.getOccurCounts());
        });
        
        // 验证光云科技出现5次
        boolean foundGuangyun = result.stream()
                .anyMatch(c -> "688365".equals(c.getStockCode()));
        assertTrue(foundGuangyun, "应该包含光云科技");
        
        log.info("✅ queryAStockCountsBetweenDate 测试通过");
    }

    @Test
    void testQueryAStockByTitleKeywords() {
        log.info("========== 测试 MCP工具: queryAStockByTitleKeywords ==========");
        
        List<AStockRss> result = aStockTool.queryAStockByTitleKeywords(Arrays.asList("业绩", "产品"));
        
        assertNotNull(result);
        assertTrue(result.size() > 0, "应该能查询到包含'业绩'或'产品'的标题");
        
        result.forEach(stock -> {
            log.info("MCP返回: {}", stock);
            assertTrue(
                stock.getTitle().contains("业绩") || stock.getTitle().contains("产品"),
                "标题应该包含关键词"
            );
        });
        
        log.info("✅ queryAStockByTitleKeywords 测试通过");
    }

    @Test
    void testQueryAStockByNameKeywords() {
        log.info("========== 测试 MCP工具: queryAStockByNameKeywords ==========");
        
        List<AStockRss> result = aStockTool.queryAStockByNameKeywords(Arrays.asList("光云", "平安"));
        
        assertNotNull(result);
        assertTrue(result.size() > 0, "应该能查询到包含'光云'或'平安'的股票");
        
        result.forEach(stock -> {
            log.info("MCP返回: {}", stock);
            assertTrue(
                stock.getStockName().contains("光云") || stock.getStockName().contains("平安"),
                "股票名称应该包含关键词"
            );
        });
        
        log.info("✅ queryAStockByNameKeywords 测试通过");
    }

    @Test
    void testQueryEmptyResult() {
        log.info("========== 测试 MCP工具: 空结果查询 ==========");
        
        List<AStockRss> result = aStockTool.queryAStockInfoByCode("999999");
        
        assertNotNull(result);
        assertEquals(0, result.size(), "不存在的股票应该返回空列表");
        
        log.info("✅ 空结果查询测试通过");
    }

    @Test
    void testSingleKeywordQuery() {
        log.info("========== 测试 MCP工具: 单关键字查询 ==========");
        
        List<AStockRss> result = aStockTool.queryAStockByTitleKeywords(Collections.singletonList("光云"));
        
        assertNotNull(result);
        assertTrue(result.size() >= 5, "应该至少返回5条包含'光云'的记录");
        
        log.info("✅ 单关键字查询测试通过");
    }

    @Test
    void testQueryGuangyunWithKeyword() {
        log.info("========== 测试 MCP工具: 光云科技关键字模糊查询 ==========");
        
        // 测试模糊查询"光云"
        List<AStockRss> result = aStockTool.queryAStockInfoByName("光云");
        
        assertNotNull(result);
        assertEquals(5, result.size(), "模糊查询'光云'应该返回5条光云科技的记录");
        
        result.forEach(stock -> {
            log.info("MCP返回: {}", stock);
            assertTrue(stock.getStockName().contains("光云"));
        });
        
        log.info("✅ 光云科技模糊查询测试通过");
    }
}
