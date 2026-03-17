package com.dawei.mcp.tool;

import com.dawei.dto.AStockEventCard;
import com.dawei.dto.AStockSignalSummary;
import com.dawei.dto.StockResolveResult;
import com.dawei.entity.AStockRss;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AStockToolTest {

    @Autowired
    private AStockTool aStockTool;

    @Test
    void resolveAStockShouldExposeCandidateStocks() {
        List<StockResolveResult> result = aStockTool.resolveAStock("茅台", 3);

        assertFalse(result.isEmpty());
        assertEquals("600519", result.get(0).getStockCode());
        assertTrue(result.get(0).getConfidence() >= 80);
    }

    @Test
    void getAStockSignalSummaryShouldExposeResearchSummary() {
        AStockSignalSummary result = aStockTool.getAStockSignalSummary("茅台", 30);

        assertNotNull(result);
        assertEquals("600519", result.getStockCode());
        assertEquals("BUY", result.getDominantSignalSide());
        assertEquals(11, result.getHighValueNoticeCount());
        assertEquals(4, result.getTopEvents().size());
    }

    @Test
    void getAStockRecentEventCardsShouldExposeClusteredCards() {
        List<AStockEventCard> result = aStockTool.getAStockRecentEventCards("茅台", 14, 60, 6);

        assertEquals(6, result.size());
        assertTrue(result.stream().anyMatch(card ->
                "600519:channel".equals(card.getClusterKey()) && card.getSupportNoticeCount() == 2));
    }

    @Test
    void boardToolsShouldExposeOpportunityAndRiskViews() {
        List<AStockEventCard> opportunityBoard = aStockTool.getAStockOpportunityBoard(72, 80, 6);
        List<AStockEventCard> riskBoard = aStockTool.getAStockRiskBoard(72, 70, 6);

        assertEquals(5, opportunityBoard.size());
        assertTrue(opportunityBoard.stream().allMatch(card -> "BUY".equals(card.getSignalSide())));
        assertEquals(1, riskBoard.size());
        assertEquals("SELL", riskBoard.get(0).getSignalSide());
    }

    @Test
    void queryRawAStockNoticesShouldRemainAsSmallFallbackTool() {
        List<AStockRss> result = aStockTool.queryRawAStockNotices("茅台", 14, 4);

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(notice -> notice.getSignalScore() >= 50));
        assertEquals(95, result.get(0).getSignalScore());
    }
}
