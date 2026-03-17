package com.dawei.mcp.tool;

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
class MacroThemeToolTest {

    @Autowired
    private MacroThemeTool macroThemeTool;

    @Test
    void getMacroThemeBoardShouldExposeMacroMainlines() {
        List<MacroThemeCard> result = macroThemeTool.getMacroThemeBoard(72, 80, 6);

        assertEquals(3, result.size());
        assertEquals("低空经济", result.get(0).getThemeName());
        assertEquals(2, result.get(0).getMappedStockCount());
    }

    @Test
    void getThemeResonanceBoardShouldExposePositiveResonanceStocks() {
        List<ResonanceStockCard> result = macroThemeTool.getThemeResonanceBoard("低空经济", 72, 6);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(card -> "低空经济".equals(card.getThemeName())));
        assertTrue(result.stream().allMatch(card -> "BUY".equals(card.getThemeSignalSide())));
    }
}
