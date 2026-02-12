package com.dawei.mcp.tool;

import com.dawei.entity.StockCounts;
import com.dawei.entity.USStockRss;
import com.dawei.service.StockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @ClassName DateTool
 * @Author 风间影月
 * @Version 1.0
 * @Description DateTool
 **/
@Component
@Slf4j
public class StockTool {

    @Resource
    private StockService stockService;

    @Tool(description = "根据股票代码查询股票信息数据")
    public List<USStockRss> queryStockInfoByCode(String stockCode) {
        log.info("========== 调用MCP工具：queryStockInfoByCode() ==========");
        log.info(String.format("| stockCode: %s", stockCode));

        return stockService.queryStock(stockCode);
    }

    @Tool(description = "根据股票代码查询某段日期内的股票信息数据")
    public List<USStockRss> queryStockInfoByCodeBetweenDate(String stockCode, String startDate, String endDate) {
        log.info("========== 调用MCP工具：queryStockInfoByCodeBetweenDate() ==========");
        log.info(String.format("| stockCode: %s", stockCode));
        log.info(String.format("| startDate: %s", startDate));
        log.info(String.format("| endDate: %s", endDate));

        return stockService.queryStockBetweenDate(stockCode, startDate, endDate);
    }


    @Tool(description = "查询某日期段内的哪些股票出现的次数超过指定的目标次数")
    public List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate) {
        log.info("========== 调用MCP工具：queryStockCountsBetweenDate() ==========");
        log.info(String.format("| targetCounts: %s", targetCounts));
        log.info(String.format("| startDate: %s", startDate));
        log.info(String.format("| endDate: %s", endDate));

        return stockService.queryStockCountsBetweenDate(targetCounts, startDate, endDate);
    }

    @Tool(description = "根据标题关键字来查询股票信息数据")
    public List<USStockRss> queryStockByTitleKeywords(List<String> titleKeywords) {
        log.info("========== 调用MCP工具：queryStockByTitleKeywords() ==========");
        log.info(String.format("| titleKeywords: %s", titleKeywords));

        return stockService.queryStockByTitleKeywords(titleKeywords);
    }

}
