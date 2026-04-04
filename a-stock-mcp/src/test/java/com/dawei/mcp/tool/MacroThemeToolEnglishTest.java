package com.dawei.mcp.tool;

import com.dawei.dto.MacroThemeCard;
import com.dawei.dto.ResonanceStockCard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MacroThemeToolEnglishTest {

    @Autowired
    private MacroThemeToolEnglish macroThemeToolEnglish;

    @Test
    void macroBoardsShouldExposeSameResearchDataInEnglishMode() {
        List<MacroThemeCard> macroBoard = macroThemeToolEnglish.getMacroThemeBoard(72, 80, 6);
        List<ResonanceStockCard> resonanceBoard = macroThemeToolEnglish.getThemeResonanceBoard("低空经济", 72, 6);

        assertEquals(3, macroBoard.size());
        assertEquals("低空经济", macroBoard.get(0).getThemeName());
        assertEquals(2, macroBoard.get(0).getMappedStockCount());
        assertEquals(2, resonanceBoard.size());
        assertTrue(resonanceBoard.stream().allMatch(card -> "低空经济".equals(card.getThemeName())));
        assertTrue(resonanceBoard.stream().allMatch(card -> "BUY".equals(card.getThemeSignalSide())));
    }
}
