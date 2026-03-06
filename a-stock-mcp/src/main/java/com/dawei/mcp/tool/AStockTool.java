package com.dawei.mcp.tool;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import com.dawei.service.AStockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @ClassName AStockTool
 * @Author dawei
 * @Version 1.0
 * @Description A股MCP工具类，提供A股数据查询服务
 **/
@Component
@Slf4j
public class AStockTool {

    @Resource
    private AStockService aStockService;

    /**
     * 根据股票代码查询A股信息
     */
    @Tool(description = "根据股票代码查询股票信息数据")
    public List<AStockRss> queryAStockInfoByCode(String stockCode) {
        log.info("========== 调用MCP工具：queryAStockInfoByCode() ==========");
        log.info("| stockCode: {}", stockCode);

        return aStockService.queryStock(stockCode);
    }

    /**
     * 根据股票名称查询A股信息
     */
    //@Tool(description = "根据股票名称查询A股股票信息数据，支持模糊查询，如：平安银行、茅台")
    public List<AStockRss> queryAStockInfoByName(String stockName) {
        log.info("========== 调用MCP工具：queryAStockInfoByName() ==========");
        log.info("| stockName: {}", stockName);

        return aStockService.queryStockByName(stockName);
    }

    /**
     * 根据股票代码和日期范围查询A股信息
     */
    @Tool(description = "根据股票代码查询某段日期内的股票信息数据，日期格式：yyyy-MM-dd")
    public List<AStockRss> queryAStockInfoByCodeBetweenDate(String stockCode, String startDate, String endDate) {
        log.info("========== 调用MCP工具：queryAStockInfoByCodeBetweenDate() ==========");
        log.info("| stockCode: {}", stockCode);
        log.info("| startDate: {}", startDate);
        log.info("| endDate: {}", endDate);

        return aStockService.queryStockBetweenDate(stockCode, startDate, endDate);
    }

    /**
     * 查询指定日期段内出现次数超过目标次数的股票
     */
    @Tool(description = "查询某日期段内的哪些股票出现的次数超过指定的目标次数")
    public List<StockCounts> queryAStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate) {
        log.info("========== 调用MCP工具：queryAStockCountsBetweenDate() ==========");
        log.info("| targetCounts: {}", targetCounts);
        log.info("| startDate: {}", startDate);
        log.info("| endDate: {}", endDate);

        return aStockService.queryStockCountsBetweenDate(targetCounts, startDate, endDate);
    }

    /**
     * 根据标题关键字查询A股信息
     */
    @Tool(description = "根据标题关键字来查询股票信息数据")
    public List<AStockRss> queryAStockByTitleKeywords(List<String> titleKeywords) {
        log.info("========== 调用MCP工具：queryAStockByTitleKeywords() ==========");
        log.info("| titleKeywords: {}", titleKeywords);

        return aStockService.queryStockByTitleKeywords(titleKeywords);
    }

    /**
     * 根据股票名称关键字查询A股信息
     */
    @Tool(description = "根据股票名称关键字来查询股票信息数据")
    public List<AStockRss> queryAStockByNameKeywords(List<String> nameKeywords) {
        log.info("========== 调用MCP工具：queryAStockByNameKeywords() ==========");
        log.info("| nameKeywords: {}", nameKeywords);

        return aStockService.queryStockByNameKeywords(nameKeywords);
    }

}
