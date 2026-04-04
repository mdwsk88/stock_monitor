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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AStockToolEnglishTest {

    @Autowired
    private AStockToolEnglish aStockToolEnglish;

    @Test
    void resolveAStockShouldExposeCandidateStocksInEnglishMode() {
        List<StockResolveResult> result = aStockToolEnglish.resolveAStock("茅台", 3);

        assertFalse(result.isEmpty());
        assertEquals("600519", result.get(0).getStockCode());
        assertTrue(result.get(0).getConfidence() >= 80);
    }

    @Test
    void getAStockSignalSummaryShouldExposeResearchSummaryInEnglishMode() {
        AStockSignalSummary result = aStockToolEnglish.getAStockSignalSummary("茅台", 30);

        assertNotNull(result);
        assertEquals("600519", result.getStockCode());
        assertEquals("BUY", result.getDominantSignalSide());
        assertEquals(180, result.getAggregateSignalScore());
        assertEquals("最近30天滚动窗口", result.getAggregateScoreWindow());
        assertEquals(11, result.getHighValueNoticeCount());
        assertEquals(4, result.getTopEvents().size());
    }

    @Test
    void boardAndRawToolsShouldDelegateCorrectlyInEnglishMode() {
        List<AStockEventCard> opportunityBoard = aStockToolEnglish.getAStockOpportunityBoard(72, 80, 6);
        List<AStockEventCard> riskBoard = aStockToolEnglish.getAStockRiskBoard(72, 70, 6);
        List<AStockRss> rawNotices = aStockToolEnglish.queryRawAStockNotices("茅台", 14, 4);

        assertEquals(4, opportunityBoard.size());
        assertTrue(opportunityBoard.stream().allMatch(card -> "BUY".equals(card.getSignalSide())));
        assertEquals(1, riskBoard.size());
        assertEquals("SELL", riskBoard.get(0).getSignalSide());
        assertEquals(4, rawNotices.size());
        assertTrue(rawNotices.stream().allMatch(notice -> notice.getSignalScore() >= 50));
    }
}
