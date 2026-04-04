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
public class AStockToolEnglish {

    @Resource
    private AStockResearchService aStockResearchService;

    @Tool(description = "Use this tool first to resolve an A-share target when the stock name or ticker is incomplete or ambiguous. "
            + "It supports a short name, full company name, or ticker such as Moutai, Kweichow Moutai, or 600519. "
            + "Returns candidate stocks, match type, confidence, and recent high-value notice count.")
    public List<StockResolveResult> resolveAStock(
            @ToolParam(description = "User-entered A-share short name, company name, or ticker") String stockQuery,
            @ToolParam(description = "Maximum candidates to return, default 5, max 10") Integer limit) {
        log.info("========== MCP Tool Call: resolveAStock() [EN] ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| limit: {}", limit);
        return aStockResearchService.resolveStocks(stockQuery, limit);
    }

    @Tool(description = "Use this tool by default when the user asks how to view a stock, whether it is worth watching, "
            + "what recent bullish or bearish signals it has, or why it appeared on a board. "
            + "Returns an aggregate stock summary including dominantSignalSide, aggregateSignalScore, topRawSignalScore, "
            + "eventClusterCount, recent high-value notice count, and topEvents. "
            + "aggregateSignalScore follows the same stock aggregation logic used by the evening report, and aggregateScoreWindow explains the query window. "
            + "When the user explicitly asks about today, after the close, or the evening report, set days=1 first. "
            + "If bestResonanceFusionScore exists, explain that it is the theme-event resonance fusion score rather than the stock aggregation score.")
    public AStockSignalSummary getAStockSignalSummary(
            @ToolParam(description = "A-share short name, company name, or ticker") String stockQuery,
            @ToolParam(description = "Lookback days, default 30, max 90") Integer days) {
        log.info("========== MCP Tool Call: getAStockSignalSummary() [EN] ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        return aStockResearchService.getStockSignalSummary(stockQuery, days);
    }

    @Tool(description = "Use this tool when the user wants recent core events for a stock. "
            + "It returns clustered event cards instead of raw disclosure rows. "
            + "signalScore is the clustered event score, rawSignalScore is the representative notice score, and scoreWindow explains the query window.")
    public List<AStockEventCard> getAStockRecentEventCards(
            @ToolParam(description = "A-share short name, company name, or ticker") String stockQuery,
            @ToolParam(description = "Lookback days, default 30") Integer days,
            @ToolParam(description = "Minimum signalScore, default 60") Integer minSignalScore,
            @ToolParam(description = "Maximum event cards to return, default 6") Integer limit) {
        log.info("========== MCP Tool Call: getAStockRecentEventCards() [EN] ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getRecentEventCards(stockQuery, days, minSignalScore, limit);
    }

    @Tool(description = "Use this tool when the user asks which bullish stocks stand out today. "
            + "Returns an opportunity board ranked by the latest rolling 24-hour aggregate stock score. "
            + "signalScore is the aggregate stock score, not the raw score of a single notice. "
            + "If theme resonance exists, fusionScore may also be returned.")
    public List<AStockEventCard> getAStockOpportunityBoard(
            @ToolParam(description = "Lookback hours, default 24") Integer hours,
            @ToolParam(description = "Minimum signalScore, default 80") Integer minSignalScore,
            @ToolParam(description = "Maximum rows to return, default 6") Integer limit) {
        log.info("========== MCP Tool Call: getAStockOpportunityBoard() [EN] ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getOpportunityBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "Use this tool when the user asks which stocks look risky or bearish today. "
            + "Returns a risk board ranked by the latest rolling 24-hour aggregate stock score. "
            + "signalScore is the aggregate stock score, not the raw score of a single notice.")
    public List<AStockEventCard> getAStockRiskBoard(
            @ToolParam(description = "Lookback hours, default 24") Integer hours,
            @ToolParam(description = "Minimum signalScore, default 70") Integer minSignalScore,
            @ToolParam(description = "Maximum rows to return, default 6") Integer limit) {
        log.info("========== MCP Tool Call: getAStockRiskBoard() [EN] ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return aStockResearchService.getRiskBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "Use this only when the user explicitly asks for raw A-share notices. "
            + "This is a fallback tool and is not suitable for direct buy or sell judgments. "
            + "By default it returns only a small set of recent notices with signalScore >= 50 to avoid context overload.")
    public List<AStockRss> queryRawAStockNotices(
            @ToolParam(description = "A-share short name, company name, or ticker") String stockQuery,
            @ToolParam(description = "Lookback days, default 30") Integer days,
            @ToolParam(description = "Maximum notices to return, default 5") Integer limit) {
        log.info("========== MCP Tool Call: queryRawAStockNotices() [EN] ==========");
        log.info("| stockQuery: {}", stockQuery);
        log.info("| days: {}", days);
        log.info("| limit: {}", limit);
        return aStockResearchService.queryRawAStockNotices(stockQuery, days, limit);
    }
}
