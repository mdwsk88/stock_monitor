package com.dawei.service;

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
class AStockResearchServiceTest {

    @Autowired
    private AStockResearchService aStockResearchService;

    @Test
    void resolveStocksShouldReturnBestCandidateForNaturalLanguageQuery() {
        List<StockResolveResult> result = aStockResearchService.resolveStocks("茅台", 3);

        assertFalse(result.isEmpty());
        assertEquals("600519", result.get(0).getStockCode());
        assertEquals("贵州茅台", result.get(0).getStockName());
        assertTrue(result.get(0).getConfidence() >= 80);
        assertEquals(11, result.get(0).getRecentHighValueNoticeCount());
    }

    @Test
    void getStockSignalSummaryShouldReturnClusteredBullishView() {
        AStockSignalSummary result = aStockResearchService.getStockSignalSummary("茅台", 30);

        assertNotNull(result);
        assertEquals("600519", result.getStockCode());
        assertEquals("BUY", result.getDominantSignalSide());
        assertEquals(11, result.getHighValueNoticeCount());
        assertEquals(9, result.getEventClusterCount());
        assertEquals(95, result.getTopSignalScore());
        assertEquals(4, result.getTopEvents().size());
        assertTrue(result.getAnalysisHint().contains("利多"));
        assertTrue(result.getTopEvents().stream().allMatch(card -> card.getSignalScore() >= 80));
    }

    @Test
    void getRecentEventCardsShouldAggregateSameClusterNotices() {
        List<AStockEventCard> result = aStockResearchService.getRecentEventCards("茅台", 14, 60, 6);

        assertEquals(6, result.size());

        AStockEventCard channelCard = result.stream()
                .filter(card -> "600519:channel".equals(card.getClusterKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, channelCard.getSupportNoticeCount());
        assertTrue(channelCard.getRelatedTitles().contains("直营网点持续扩容"));

        AStockEventCard buybackCard = result.stream()
                .filter(card -> "600519:buyback".equals(card.getClusterKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, buybackCard.getSupportNoticeCount());
        assertEquals("BUY", buybackCard.getSignalSide());
    }

    @Test
    void getOpportunityBoardShouldReturnHighScoreBullishCardsWithStockDedup() {
        List<AStockEventCard> result = aStockResearchService.getOpportunityBoard(72, 80, 6);

        assertEquals(5, result.size());
        assertEquals("600519", result.get(0).getStockCode());
        assertTrue(result.stream().allMatch(card -> "BUY".equals(card.getSignalSide())));
        assertTrue(result.stream().map(AStockEventCard::getStockCode).distinct().count() == result.size());
    }

    @Test
    void getRiskBoardShouldReturnSellSideBoard() {
        List<AStockEventCard> result = aStockResearchService.getRiskBoard(72, 70, 6);

        assertEquals(1, result.size());
        assertEquals("300308", result.get(0).getStockCode());
        assertEquals("SELL", result.get(0).getSignalSide());
        assertEquals(88, result.get(0).getSignalScore());
    }

    @Test
    void queryRawAStockNoticesShouldRemainBounded() {
        List<AStockRss> result = aStockResearchService.queryRawAStockNotices("茅台", 14, 4);

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(notice -> notice.getSignalScore() >= 50));
        assertEquals(95, result.get(0).getSignalScore());
    }
}
