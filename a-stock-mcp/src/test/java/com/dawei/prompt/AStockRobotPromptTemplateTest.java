package com.dawei.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStockRobotPromptTemplateTest {

    @Test
    void systemPromptShouldPrioritizeResearchToolsBeforeRawNotices() {
        AStockRobotPromptTemplate template = new AStockRobotPromptTemplate();

        String prompt = template.systemPrompt();

        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("resolveAStock"));
        assertTrue(prompt.contains("getAStockSignalSummary"));
        assertTrue(prompt.contains("getMacroThemeBoard"));
        assertTrue(prompt.contains("getThemeResonanceBoard"));
        assertTrue(prompt.contains("queryRawAStockNotices"));
        assertTrue(prompt.indexOf("getAStockSignalSummary") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.indexOf("getThemeResonanceBoard") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.contains("除非确实需要原始明细，否则不要直接使用 `queryRawAStockNotices`"));
    }
}
