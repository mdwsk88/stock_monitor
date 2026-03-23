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
        assertTrue(prompt.contains("aggregateScoreWindow"));
        assertTrue(prompt.contains("days=1"));
        assertTrue(prompt.contains("不完全等同晚报固定的今日09:00-15:00交易时段"));
        assertTrue(prompt.indexOf("getAStockSignalSummary") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.indexOf("getThemeResonanceBoard") < prompt.indexOf("queryRawAStockNotices"));
        assertTrue(prompt.contains("除非确实需要原始明细，否则不要直接使用 `queryRawAStockNotices`"));
        assertTrue(prompt.contains("通用概念题"));
        assertTrue(prompt.contains("不要硬调 MCP，直接给出简洁、结构化的解释"));
        assertTrue(prompt.contains("超出当前工具覆盖的题"));
        assertTrue(prompt.contains("坦白说能力边界，不要编造实时价格、盘口或财务指标"));
        assertTrue(prompt.contains("# 核心认知与行为边界（Highest Priority）"));
        assertTrue(prompt.contains("您好，我是专属的量化投研 AI，只处理 A 股市场的核心资讯与逻辑推演"));
        assertTrue(prompt.contains("我无法在缺乏最新数据支撑的情况下为您提供投研判断"));
        assertTrue(prompt.contains("绝对不允许出现“满仓”“清仓”“梭哈”“必涨”"));
        assertTrue(prompt.contains("💡 您可以尝试这样问我"));
        assertTrue(prompt.contains("固定降级话术矩阵"));
        assertTrue(prompt.contains("当前 MCP 不覆盖这类实时或底层数据"));
        assertTrue(prompt.contains("优先调用 `resolveAStock`；如果仍然无法确认，必须先追问用户"));
    }
}
