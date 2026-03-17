package com.dawei.service;

import com.dawei.dto.MacroThemeCard;
import com.dawei.dto.ResonanceStockCard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MacroThemeResearchServiceTest {

    @Autowired
    private MacroThemeResearchService macroThemeResearchService;

    @Test
    void getMacroThemeBoardShouldGroupSameClusterAndExposeMappedStocks() {
        List<MacroThemeCard> result = macroThemeResearchService.getMacroThemeBoard(72, 80, 6);

        assertEquals(3, result.size());

        MacroThemeCard lowAltitude = result.get(0);
        assertEquals("低空经济", lowAltitude.getThemeName());
        assertEquals(93, lowAltitude.getSignalScore());
        assertEquals(2, lowAltitude.getSupportEventCount());
        assertEquals(2, lowAltitude.getMappedStockCount());
        assertTrue(lowAltitude.getMappedStocks().contains("宗申动力"));
        assertTrue(lowAltitude.getMappedStocks().contains("万丰奥威"));

        assertTrue(result.stream().anyMatch(card ->
                "创新药".equals(card.getThemeName()) && "SELL".equals(card.getSignalSide())));
    }

    @Test
    void getThemeResonanceBoardShouldReturnOnlyBullishResonanceCandidates() {
        List<ResonanceStockCard> result = macroThemeResearchService.getThemeResonanceBoard(null, 72, 6);

        assertEquals(3, result.size());
        assertEquals("001696", result.get(0).getStockCode());
        assertTrue(result.stream().allMatch(card -> "BUY".equals(card.getThemeSignalSide())));
        assertFalse(result.stream().anyMatch(card -> "688235".equals(card.getStockCode())));
    }

    @Test
    void getThemeResonanceBoardShouldSupportThemeFilter() {
        List<ResonanceStockCard> result = macroThemeResearchService.getThemeResonanceBoard("算力", 72, 6);

        assertEquals(1, result.size());
        assertEquals("算力", result.get(0).getThemeName());
        assertEquals("300308", result.get(0).getStockCode());
        assertTrue(result.get(0).getRelatedEventTitle().contains("算力基础设施建设持续加码"));
    }
}
