package com.dawei.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStockRobotPromptTemplateTest {

    @Test
    void systemPromptShouldPrioritizeResearchToolsBeforeRawNotices() {
        AStockRobotPromptTemplate template = new AStockRobotPromptTemplate("zh");

        String prompt = template.systemPrompt();

        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("resolveAStock"));
        assertTrue(prompt.contains("getAStockSignalSummary"));
        assertTrue(prompt.contains("getMacroThemeBoard"));
        assertTrue(prompt.contains("getThemeResonanceBoard"));
        assertTrue(prompt.contains("queryRawAStockNotices"));
        assertTrue(prompt.contains("aggregateScoreWindow"));
        assertTrue(prompt.contains("days=1"));
        assertTrue(prompt.contains("不完全等同晚报固定交易时段"));
        assertTrue(prompt.indexOf("getAStockSignalSummary") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.indexOf("getThemeResonanceBoard") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.contains("通用概念题"));
        assertTrue(prompt.contains("可以直接回答，不需要为了用工具而乱调 MCP"));
        assertTrue(prompt.contains("未覆盖题"));
        assertTrue(prompt.contains("坦白说明边界，不要编造实时价格、盘口、估值或财务指标"));
        assertTrue(prompt.contains("# 核心边界（Highest Priority）"));
        assertTrue(prompt.contains("您好，我是专属的量化投研 AI，只处理 A 股市场的核心资讯与逻辑推演"));
        assertTrue(prompt.contains("我无法在缺乏最新数据支撑的情况下为您提供投研判断"));
        assertTrue(prompt.contains("不能给直接交易指令"));
        assertTrue(prompt.contains("💡 您可以尝试这样问我"));
        assertTrue(prompt.contains("当前 MCP 不覆盖这类实时或底层数据"));
        assertTrue(prompt.contains("优先调用 `resolveAStock`"));
    }

    @Test
    void systemPromptShouldLoadEnglishTemplateWhenLanguageIsEnglish() {
        AStockRobotPromptTemplate template = new AStockRobotPromptTemplate("en");

        String prompt = template.systemPrompt();

        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("All visible output"));
        assertTrue(prompt.contains("resolveAStock"));
        assertTrue(prompt.contains("getAStockSignalSummary"));
        assertTrue(prompt.contains("getMacroThemeBoard"));
        assertTrue(prompt.contains("getThemeResonanceBoard"));
        assertTrue(prompt.contains("queryRawAStockNotices"));
        assertTrue(prompt.contains("Reasoning: if shown, keep it in English."));
        assertTrue(prompt.contains("No high-value event is currently detected."));
        assertTrue(prompt.contains("resonance fusion"));
        assertTrue(prompt.indexOf("getAStockSignalSummary") < prompt.indexOf("queryRawAStockNotices"));
    }
}
