package com.dawei.mcp.tool;

import com.dawei.dto.AStockEventCard;
import com.dawei.dto.AStockSignalSummary;
import com.dawei.dto.StockResolveResult;
import com.dawei.entity.AStockRss;
import com.dawei.service.AStockResearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class AStockTool {

    @Resource
    private AStockResearchService aStockResearchService;

    @Tool(description = "先用这个工具解析用户口中的A股标的。"
            + "适用于用户输入股票简称、全称或代码，例如：茅台、贵州茅台、600519。"
            + "返回候选股票、匹配方式、置信度和最近高价值公告数量。")
    public List<StockResolveResult> resolveAStock(
            @ToolParam(description = "用户输入的股票简称、公司名或代码") String stockQuery,
            @ToolParam(description = "返回候选数量，默认 5，最大 10") Integer limit) {
        log.info("========== 调用MCP工具：resolveAStock() ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| limit: {}", limit);
        return aStockResearchService.resolveStocks(stockQuery, limit);
    }

    @Tool(description = "当用户询问某只股票最近怎么看、有什么利好利空、值不值得关注时优先使用。"
            + "返回一个聚合摘要，包含 dominantSignalSide、事件簇数量、最近高价值公告数和 topEvents。"
            + "默认看最近 30 天，已经过滤掉低价值行政公告。")
    public AStockSignalSummary getAStockSignalSummary(
            @ToolParam(description = "股票简称、公司名或代码") String stockQuery,
            @ToolParam(description = "回看天数，默认 30，最大 90") Integer days) {
        log.info("========== 调用MCP工具：getAStockSignalSummary() ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        return aStockResearchService.getStockSignalSummary(stockQuery, days);
    }

    @Tool(description = "当用户想看某只股票最近有哪些核心事件时使用。"
            + "返回事件卡片而不是原始公告流水，每张卡片都按 clusterKey 聚合，包含代表标题、signalScore、signalSide 和 supportNoticeCount。")
    public List<AStockEventCard> getAStockRecentEventCards(
            @ToolParam(description = "股票简称、公司名或代码") String stockQuery,
            @ToolParam(description = "回看天数，默认 30") Integer days,
            @ToolParam(description = "最小 signalScore，默认 60") Integer minSignalScore,
            @ToolParam(description = "最多返回事件卡片数，默认 6") Integer limit) {
        log.info("========== 调用MCP工具：getAStockRecentEventCards() ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getRecentEventCards(stockQuery, days, minSignalScore, limit);
    }

    @Tool(description = "当用户问今天看什么票、今天有哪些值得关注的机会股时使用。"
            + "返回最近 24 小时内高分利多事件构成的机会榜，默认只看 signalScore>=80 的事件，并对同一股票去重。")
    public List<AStockEventCard> getAStockOpportunityBoard(
            @ToolParam(description = "回看小时数，默认 24") Integer hours,
            @ToolParam(description = "最小 signalScore，默认 80") Integer minSignalScore,
            @ToolParam(description = "返回数量，默认 6") Integer limit) {
        log.info("========== 调用MCP工具：getAStockOpportunityBoard() ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getOpportunityBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "当用户问今天有哪些雷、哪些股票偏利空时使用。"
            + "返回最近 24 小时内高分利空事件构成的风险榜，默认只看 signalScore>=70 的 SELL 事件，并对同一股票去重。")
    public List<AStockEventCard> getAStockRiskBoard(
            @ToolParam(description = "回看小时数，默认 24") Integer hours,
            @ToolParam(description = "最小 signalScore，默认 70") Integer minSignalScore,
            @ToolParam(description = "返回数量，默认 6") Integer limit) {
        log.info("========== 调用MCP工具：getAStockRiskBoard() ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getRiskBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "仅在需要查看原始公告明细时使用。"
            + "这是一个兜底工具，不适合直接回答买卖建议。"
            + "默认只返回最近 30 天内 signalScore>=50 的少量公告，避免上下文爆炸。")
    public List<AStockRss> queryRawAStockNotices(
            @ToolParam(description = "股票简称、公司名或代码") String stockQuery,
            @ToolParam(description = "回看天数，默认 30") Integer days,
            @ToolParam(description = "最多返回公告数，默认 5") Integer limit) {
        log.info("========== 调用MCP工具：queryRawAStockNotices() ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        log.info("| limit: {}", limit);
        return aStockResearchService.queryRawAStockNotices(stockQuery, days, limit);
    }
}
