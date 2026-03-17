package com.dawei.service.impl;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.StockAlertDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AISummaryServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private AISummaryServiceImpl aiSummaryService;

    @BeforeEach
    void setUp() {
        aiSummaryService = new AISummaryServiceImpl(chatClient);
        lenient().when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    @DisplayName("A股摘要会清洗模型返回的 Markdown 代码块")
    void summarizeAStocks_StripsMarkdownFence() {
        when(callResponseSpec.content()).thenReturn("```markdown\n平安银行：重大合同驱动，偏利多。\n```");

        String result = aiSummaryService.summarizeAStocks(List.of(buildNotice()));

        assertFalse(result.contains("```"));
        assertTrue(result.contains("重大合同驱动"));
    }

    @Test
    @DisplayName("A股盘前早报会向模型传递机会榜和风险榜的结构化事件卡片")
    void generateAMorningReportMarkdown_PassesStructuredEventCardPrompt() {
        when(callResponseSpec.content()).thenReturn("""
                ```markdown
                # 🌅 A股盘前异动雷达 | 2026-03-15

                ## 宏观主线

                > 1. 算力 | 政策扶持

                ## 共振标的

                > 1. 平安银行 (000001) | 算力

                ## 机会榜

                > 1. 平安银行 (000001) | 🇨🇳 A股

                ## 风险榜

                > 1. *ST亚太 (000691) | 🇨🇳 A股
                ```
                """);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        String markdown = aiSummaryService.generateAMorningReportMarkdown(
                buildFusionContext(),
                "2026-03-15"
        );

        verify(chatClient).prompt(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getContents();

        assertFalse(markdown.contains("```"));
        assertTrue(markdown.startsWith("# 🌅 A股盘前异动雷达 | 2026-03-15"));
        assertTrue(promptText.contains("## MacroThemeCandidates"));
        assertTrue(promptText.contains("## ResonanceCandidates"));
        assertTrue(promptText.contains("## OpportunityCandidates"));
        assertTrue(promptText.contains("## RiskCandidates"));
        assertTrue(promptText.contains("theme_name: 算力"));
        assertTrue(promptText.contains("mapped_stock_count: 3"));
        assertTrue(promptText.contains("fusion_score: 136"));
        assertTrue(promptText.contains("macro_theme_name: 算力"));
        assertTrue(promptText.contains("### EventCard 1"));
        assertTrue(promptText.contains("signal_score: 118"));
        assertTrue(promptText.contains("signal_level: 主线级"));
        assertTrue(promptText.contains("signal_side: 利多"));
        assertTrue(promptText.contains("event_cluster_count: 2"));
        assertTrue(promptText.contains("support_notice_count: 4"));
        assertTrue(promptText.contains("analysis_hint: 最高优先级事件为【重大合同】"));
        assertTrue(promptText.contains("cluster_highlights:"));
        assertTrue(promptText.contains("中标10亿元算力项目"));
        assertTrue(promptText.contains("stock_name: *ST亚太"));
        assertTrue(promptText.contains("signal_side: 利空"));
        assertTrue(promptText.contains("event_type: 监管处罚"));
    }

    @Test
    @DisplayName("A股盘前早报遇到非 Markdown 输出时会回退到双榜规则模板")
    void generateAMorningReportMarkdown_FallbacksWhenModelOutputInvalid() {
        when(callResponseSpec.content()).thenReturn("平安银行偏利多，关注盘前表现");

        String markdown = aiSummaryService.generateAMorningReportMarkdown(
                buildFusionContext(),
                "2026-03-15"
        );

        assertTrue(markdown.startsWith("# 🌅 A股盘前异动雷达 | 2026-03-15"));
        assertTrue(markdown.contains("## 宏观主线"));
        assertTrue(markdown.contains("## 共振标的"));
        assertTrue(markdown.contains("## 机会榜"));
        assertTrue(markdown.contains("## 风险榜"));
        assertTrue(markdown.contains("🎯 事件评分"));
        assertTrue(markdown.contains("主题强度"));
        assertTrue(markdown.contains("共振强度"));
        assertTrue(markdown.contains("118 分"));
        assertTrue(markdown.contains("2 个事件簇 / 4 条支撑公告"));
        assertTrue(markdown.contains("平安银行:关于中标10亿元算力项目的公告"));
        assertTrue(markdown.contains("*ST亚太"));
        assertTrue(markdown.contains("利空预警"));
    }

    @Test
    @DisplayName("A股盘后复盘遇到非 Markdown 输出时会回退到双榜规则模板")
    void generateAEveningReportMarkdown_FallbacksWhenModelOutputInvalid() {
        when(callResponseSpec.content()).thenReturn("今日核心是监管风险");

        String markdown = aiSummaryService.generateAEveningReportMarkdown(
                buildFusionContext(),
                "2026-03-15"
        );

        assertTrue(markdown.startsWith("# 🌆 A股盘后情绪解码 | 2026-03-15"));
        assertTrue(markdown.contains("## 宏观主线"));
        assertTrue(markdown.contains("## 共振标的"));
        assertTrue(markdown.contains("## 机会榜"));
        assertTrue(markdown.contains("## 风险榜"));
        assertTrue(markdown.contains("当日热度"));
        assertTrue(markdown.contains("*ST亚太"));
        assertTrue(markdown.contains("平安银行"));
    }

    @Test
    @DisplayName("A股盘后复盘无数据时返回晚报模板而不是盘前模板")
    void generateAEveningReportMarkdown_UsesEveningNoDataTemplate() {
        String markdown = aiSummaryService.generateAEveningReportMarkdown(List.of(), "2026-03-15");

        assertTrue(markdown.startsWith("# 🌆 A股盘后情绪解码 | 2026-03-15"));
        assertTrue(markdown.contains("今日 A 股已收盘"));
        assertTrue(markdown.contains("盘后事件"));
    }

    @Test
    @DisplayName("A股盘前早报融合上下文为空时返回无数据模板")
    void generateAMorningReportMarkdown_UsesNoDataTemplateWhenFusionContextEmpty() {
        String markdown = aiSummaryService.generateAMorningReportMarkdown(new AReportFusionContext(), "2026-03-15");

        assertTrue(markdown.startsWith("# 🌅 AI 盘前异动雷达 | 2026-03-15"));
        assertTrue(markdown.contains("暂无A股异动数据"));
    }

    private AStockRss buildNotice() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("000001");
        stock.setStockName("平安银行");
        stock.setTitle("平安银行:关于中标10亿元算力项目的公告");
        stock.setTag("重大合同");
        stock.setEventType("重大合同");
        stock.setSignalSide("利多");
        stock.setSignalScore(118);
        stock.setPubDate(LocalDateTime.of(2026, 3, 15, 9, 30));
        stock.setAnalysisHint("最高优先级事件为【重大合同】，方向=利多，总评分=118 分");
        stock.setClusterHighlights("重大合同 | 方向=利多 | 簇评分=118 | 支撑公告=4 | 代表标题=平安银行:关于中标10亿元算力项目的公告");
        return stock;
    }

    private StockAlertDTO<AStockRss> buildOpportunityAlert() {
        AStockRss stock = buildNotice();
        stock.setRelatedTitles("平安银行:关于中标10亿元算力项目的公告 | 平安银行:关于签署5亿元智算合同的公告");
        stock.setEventCount(2);
        stock.setRawNoticeCount(4);
        return new StockAlertDTO<>(stock, 4, 118, 2, "利多");
    }

    private StockAlertDTO<AStockRss> buildRiskAlert() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("000691");
        stock.setStockName("*ST亚太");
        stock.setTitle("*ST亚太:关于收到立案告知书及退市风险提示的公告");
        stock.setTag("风险提示性公告");
        stock.setEventType("监管处罚");
        stock.setSignalSide("利空");
        stock.setSignalScore(112);
        stock.setPubDate(LocalDateTime.of(2026, 3, 15, 10, 15));
        stock.setAnalysisHint("最高优先级事件为【监管处罚】，方向=利空，总评分=112 分");
        stock.setClusterHighlights("监管处罚 | 方向=利空 | 簇评分=112 | 支撑公告=2 | 代表标题=*ST亚太:关于收到立案告知书及退市风险提示的公告");
        stock.setRelatedTitles("*ST亚太:关于收到立案告知书及退市风险提示的公告 | *ST亚太:关于公司股票可能被实施退市风险警示的提示公告");
        stock.setEventCount(1);
        stock.setRawNoticeCount(2);
        return new StockAlertDTO<>(stock, 2, 112, 1, "利空");
    }

    private AReportFusionContext buildFusionContext() {
        MacroThemeEvent macroTheme = new MacroThemeEvent();
        macroTheme.setThemeName("算力");
        macroTheme.setEventType("政策扶持");
        macroTheme.setSignalSide("利多");
        macroTheme.setSignalScore(90);
        macroTheme.setMappedStockCount(3);
        macroTheme.setMappedStocks("平安银行(000001)、浪潮信息(000977)、中科曙光(603019)");
        macroTheme.setTitle("工信部推进算力基础设施建设");
        macroTheme.setSummary("算力基础设施建设再获政策强化");

        AReportResonanceCard resonanceCard = new AReportResonanceCard();
        resonanceCard.setStockCode("000001");
        resonanceCard.setStockName("平安银行");
        resonanceCard.setSignalSide("利多");
        resonanceCard.setFusionScore(136);
        resonanceCard.setNoticeSignalScore(118);
        resonanceCard.setMacroSignalScore(90);
        resonanceCard.setEventClusterCount(2);
        resonanceCard.setSupportNoticeCount(4);
        resonanceCard.setMacroThemeName("算力");
        resonanceCard.setMacroEventType("政策扶持");
        resonanceCard.setNoticeEventType("重大合同");
        resonanceCard.setRelationReason("宏观主题映射命中：平安银行(000001)");
        resonanceCard.setNoticeTitle("平安银行:关于中标10亿元算力项目的公告");
        resonanceCard.setMacroTitle("工信部推进算力基础设施建设");
        resonanceCard.setMacroSummary("算力基础设施建设再获政策强化");
        resonanceCard.setNoticeAnalysisHint("最高优先级事件为【重大合同】，方向=利多，总评分=118 分");

        AReportFusionContext context = new AReportFusionContext();
        context.setMacroThemes(List.of(macroTheme));
        context.setResonanceCandidates(List.of(resonanceCard));
        context.setOpportunityAlerts(List.of(buildOpportunityAlert()));
        context.setRiskAlerts(List.of(buildRiskAlert()));
        return context;
    }
}
