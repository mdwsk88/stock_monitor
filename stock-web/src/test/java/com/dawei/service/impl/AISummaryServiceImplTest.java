package com.dawei.service.impl;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.StockAlertDTO;
import com.dawei.utils.PushLanguageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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
        assertTrue(markdown.contains("口径说明"));
        assertTrue(markdown.contains("最近24小时窗口内的股票聚合分"));
        assertTrue(promptText.contains("## MarketContext"));
        assertTrue(promptText.contains("market_state: 进攻态"));
        assertTrue(promptText.contains("market_interpretation: 盘面进入进攻态"));
        assertTrue(promptText.contains("## MacroThemeCandidates"));
        assertTrue(promptText.contains("## ResonanceCandidates"));
        assertTrue(promptText.contains("## OpportunityCandidates"));
        assertTrue(promptText.contains("## RiskCandidates"));
        assertTrue(promptText.contains("theme_name: 算力"));
        assertTrue(promptText.contains("mapped_stock_count: 3"));
        assertTrue(promptText.contains("fusion_score: 136"));
        assertTrue(promptText.contains("macro_theme_name: 算力"));
        assertTrue(promptText.contains("position_label: 领军核心"));
        assertTrue(promptText.contains("position_reason: 事件评分进入主线级"));
        assertTrue(promptText.contains("trade_hint: 可作为主线锚点"));
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
        assertTrue(markdown.contains("口径说明"));
        assertTrue(markdown.contains("当前市场状态：进攻态"));
        assertTrue(markdown.contains("🎯 事件评分"));
        assertTrue(markdown.contains("主题强度"));
        assertTrue(markdown.contains("共振强度"));
        assertTrue(markdown.contains("🏷️ 身位判定"));
        assertTrue(markdown.contains("领军核心"));
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
        assertTrue(markdown.contains("口径说明"));
        assertTrue(markdown.contains("今日 09:00-15:00 交易时段内的股票聚合分"));
        assertTrue(markdown.contains("当前市场状态：进攻态"));
        assertTrue(markdown.contains("🏷️ 身位判定"));
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

    @Test
    @DisplayName("英文模式下会向模型追加语言要求并翻译结构化上下文字段")
    void generateAMorningReportMarkdown_EnglishModeAddsLanguageInstructionAndEnglishPromptFields() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("""
                # A-Stock Pre-Market Alert Radar | 2026-03-15

                ## Macro Themes

                ## Resonance Picks

                ## Opportunity Board

                ## Risk Board
                """);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        String markdown = englishService.generateAMorningReportMarkdown(buildFusionContext(), "2026-03-15");

        verify(chatClient).prompt(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getContents();

        assertTrue(markdown.contains("A-Stock Pre-Market Alert Radar | 2026-03-15"));
        assertTrue(promptText.contains("[Language Requirement]"));
        assertTrue(promptText.contains("[Role]"));
        assertTrue(promptText.contains("The final markdown must be fully in English."));
        assertTrue(promptText.contains("market_state: Risk-On"));
        assertTrue(promptText.contains("signal_side: Bullish"));
        assertTrue(promptText.contains("signal_level: Main-Theme Tier"));
        assertTrue(promptText.contains("position_label: Leading Core"));
        assertTrue(promptText.contains("position_reason: Event score reached main-theme tier"));
        assertTrue(promptText.contains("trade_hint: Can serve as a main-theme anchor"));
        assertTrue(promptText.contains("relation_reason: Macro-theme mapping hit: 平安银行(000001)"));
        assertFalse(promptText.contains("【角色设定】"));
    }

    @Test
    @DisplayName("英文模式下 A 股回退模板会输出英文分栏与口径说明")
    void generateAMorningReportMarkdown_EnglishFallbackUsesEnglishSections() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("Ping An Bank looks constructive before the open.");

        String markdown = englishService.generateAMorningReportMarkdown(buildFusionContext(), "2026-03-15");

        assertTrue(markdown.contains("A-Stock Pre-Market Alert Radar | 2026-03-15"));
        assertTrue(markdown.contains("Methodology:"));
        assertTrue(markdown.contains("## Macro Themes"));
        assertTrue(markdown.contains("## Resonance Picks"));
        assertTrue(markdown.contains("## Opportunity Board"));
        assertTrue(markdown.contains("## Risk Board"));
        assertTrue(markdown.contains("Current market regime: Risk-On"));
        assertTrue(markdown.contains("Positioning"));
        assertTrue(markdown.contains("Leading Core"));
    }

    @Test
    @DisplayName("英文模式下会规范化盘前标题、开场白和固定中文标签")
    void generateAMorningReportMarkdown_EnglishModeNormalizesChineseLeadAndLabels() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("""
                # 🌅 A股盘前异动雷达 | 2026-03-15

                过去 24 小时内，系统完成了公告去噪、事件聚类和风险分流，以下标的是盘前最值得关注的机会与风险：

                ## Macro Themes

                > 1. 货币政策 | Liquidity Easing
                > 🧭 主线方向：<font color="success">[利多]</font>
                > 🎯 主题强度：106 分 (高优先级，关联 2 只映射标的)
                > 🧠 主线解读：The Fed's hold-probability remains elevated.

                ## Resonance Picks

                > 1. 平安银行 (000001) | 算力
                > 🔗 共振强度：136 分 (强共振)

                ## Opportunity Board

                > 1. 平安银行 (000001) | 🇨🇳 A股
                > 📈 事件判断：<font color="warning">【强烈看多】</font>
                > 🎯 事件评分：118 分 (主线级，2 个事件簇 / 4 条支撑公告)

                ## Risk Board

                > 1. *ST亚太 (000691) | 🇨🇳 A股
                > ⚠️ 事件判断：<font color="warning">【利空预警】</font>
                > 🎯 事件评分：86 分 (高优先级，1 个事件簇 / 2 条支撑公告)
                """);

        String markdown = englishService.generateAMorningReportMarkdown(buildFusionContext(), "2026-03-15");

        assertTrue(markdown.startsWith("# 🌅 A-Stock Pre-Market Alert Radar | 2026-03-15"));
        assertTrue(markdown.contains("Over the last 24 hours, the system completed notice de-noising"));
        assertTrue(markdown.contains("> 🧭 Direction: <font color=\"success\">[Bullish]</font>"));
        assertTrue(markdown.contains("> 🎯 Theme Strength: 106 points (High Priority, associated with 2 mapped stocks)"));
        assertTrue(markdown.contains("> 🔗 Resonance Score: 136 points (Strong Resonance)"));
        assertTrue(markdown.contains("> 📈 Event View: <font color=\"warning\">【Strong Bullish】</font>"));
        assertTrue(markdown.contains("> 🎯 Event Score: 118 points (Main-Theme Tier, 2 event clusters / 4 supporting notices)"));
        assertFalse(markdown.contains("A股盘前异动雷达"));
        assertFalse(markdown.contains("过去 24 小时内"));
        assertFalse(markdown.contains("主线方向"));
    }

    @Test
    @DisplayName("英文模式下若首轮结果仍混入中文，会使用更严格英文模板重试")
    void generateAMorningReportMarkdown_EnglishModeRetriesWithStrictEnglishPrompt() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("""
                # 🌅 A股盘前异动雷达 | 2026-03-15

                过去 24 小时内，系统完成了公告去噪、事件聚类和风险分流。

                ## Macro Themes

                > 1. 国家政策 | Policy Release
                > 🧭 方向判断：<font color="success">[利多]</font>
                > 🎯 主题强度：112 分 (主线级，关联 0 只映射标的)
                > 🧠 盘面影响解码：国务院发文提升中小微信用贷款可得性。

                ## Resonance Picks

                <font color="comment">暂无公告与主题共振标的</font>

                ## Opportunity Board

                > 1. 鼎龙股份 (300054) | 🇨🇳 A股
                > 📈 事件判断：<font color="warning">【强烈看多】</font>
                > 🧠 核心预期差：公告集中披露打印耗材业务订单。

                ## Risk Board

                <font color="comment">暂无高优先级风险事件</font>
                """, """
                # 🌅 A-Stock Pre-Market Alert Radar | 2026-03-15

                Over the last 24 hours, the system completed notice de-noising, event clustering, macro-theme aggregation, and risk routing. These are the main themes, resonance picks, opportunities, and risks worth watching before the open:

                ## Macro Themes

                > 1. National Policy | Policy Release
                > 🧭 Direction: <font color="success">[Bullish]</font>
                > 🎯 Theme Strength: 112 points (Main-Theme Tier, associated with 0 mapped stocks)
                > 🧠 Interpretation: Policy support improved credit availability for small and micro businesses.

                ## Resonance Picks

                <font color="comment">No notice-theme resonance pick met the threshold</font>

                ## Opportunity Board

                > 1. 鼎龙股份 (300054) | 🇨🇳 Stock
                > 📈 Event View: <font color="warning">【Strong Bullish】</font>
                > 🧠 Core Setup: Concentrated disclosure around printing-material orders strengthened demand expectations.

                ## Risk Board

                <font color="comment">No high-priority risk event was detected</font>
                """);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        String markdown = englishService.generateAMorningReportMarkdown(buildFusionContext(), "2026-03-15");

        verify(chatClient, times(2)).prompt(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();

        assertTrue(prompts.get(0).getContents().contains("[Role]"));
        assertTrue(prompts.get(0).getContents().contains("The final markdown must be fully in English."));
        assertTrue(prompts.get(1).getContents().contains("[Retry Requirement]"));
        assertTrue(markdown.contains("Policy support improved credit availability for small and micro businesses."));
        assertTrue(markdown.contains("No notice-theme resonance pick met the threshold"));
        assertFalse(markdown.contains("国务院发文"));
        assertFalse(markdown.contains("暂无公告与主题共振标的"));
    }

    @Test
    @DisplayName("英文模式下会规范化盘后标题、开场白和固定中文标签")
    void generateAEveningReportMarkdown_EnglishModeNormalizesChineseLeadAndLabels() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("""
                # 🌆 A股盘后情绪解码 | 2026-03-15

                今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并叠加宏观主题线索，拆分出机会、风险与共振三条主线：

                ## Macro Themes

                > 1. 人工智能 | Policy Support | Cycle Catalyst
                > 🧭 主线方向：<font color="success">[利多]</font>
                > 🎯 主题强度：90 分 (高优先级，关联 15 只映射标的)
                > 🧠 主线解读：Policy support continued to anchor AI application expectations.

                ## Resonance Picks

                > 1. 优博讯 (300531) | 人工智能
                > 🔗 共振强度：142 分 (强共振)

                ## Opportunity Board

                > 1. 优博讯 (300531) | 🇨🇳 A股
                > 🔥 当日热度：<font color="warning">主线级 (事件评分 113 分，2 个事件簇 / 5 条支撑公告)</font>
                > 🧠 涨跌逻辑解码：[AI分析中...] AI terminal momentum remained firm.

                ## Risk Board

                > 1. *ST亚太 (000691) | 🇨🇳 A股
                > 📉 当日热度：<font color="warning">高优先级 (事件评分 86 分，1 个事件簇 / 2 条支撑公告)</font>
                > 🧠 涨跌逻辑解码：[AI分析中...] Risk notices kept pressuring sentiment.
                """);

        String markdown = englishService.generateAEveningReportMarkdown(buildFusionContext(), "2026-03-15");

        assertTrue(markdown.startsWith("# 🌆 A-Stock Post-Close Decode | 2026-03-15"));
        assertTrue(markdown.contains("The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00"));
        assertTrue(markdown.contains("> 🧭 Direction: <font color=\"success\">[Bullish]</font>"));
        assertTrue(markdown.contains("> 🎯 Theme Strength: 90 points (High Priority, associated with 15 mapped stocks)"));
        assertTrue(markdown.contains("> 🔗 Resonance Score: 142 points (Strong Resonance)"));
        assertTrue(markdown.contains("> 🔥 Session Heat: <font color=\"warning\">Main-Theme Tier (Event score 113 points, 2 event clusters / 5 supporting notices)</font>"));
        assertTrue(markdown.contains("> 📉 Session Heat: <font color=\"warning\">High Priority (Event score 86 points, 1 event clusters / 2 supporting notices)</font>"));
        assertTrue(markdown.contains("> 🧠 Price Action Decode: [AI reviewing...] AI terminal momentum remained firm."));
        assertFalse(markdown.contains("A股盘后情绪解码"));
        assertFalse(markdown.contains("今日 A 股已收盘"));
        assertFalse(markdown.contains("主线方向"));
    }

    @Test
    @DisplayName("英文模式下盘后复盘若首轮仍混入中文，会使用更严格英文模板重试")
    void generateAEveningReportMarkdown_EnglishModeRetriesWithStrictEnglishPrompt() {
        AISummaryServiceImpl englishService = new AISummaryServiceImpl(chatClient, new AStockReportClassifier(), new PushLanguageService("en"));
        when(callResponseSpec.content()).thenReturn("""
                # 🌆 A股盘后情绪解码 | 2026-03-15

                今日 A 股已收盘。系统回溯了日内公告事件。

                ## Macro Themes

                > 1. 算力 | Policy Support
                > 🧭 方向判断：<font color="success">[利多]</font>
                > 🎯 主题强度：90 分 (高优先级，关联 14 只映射标的)
                > 🧠 盘面影响解码：产业链订单与政策共振，吸引资金回流。

                ## Resonance Picks

                <font color="comment">暂无公告与主题共振标的</font>

                ## Opportunity Board

                > 1. 优博讯 (300531) | 🇨🇳 A股
                > 🔥 当日热度：<font color="warning">主线级 (事件评分 113 分，2 个事件簇 / 5 条支撑公告)</font>
                > 🧠 涨跌逻辑解码：AI 终端合作继续发酵。

                ## Risk Board

                <font color="comment">暂无高优先级风险事件</font>
                """, """
                # 🌆 A-Stock Post-Close Decode | 2026-03-15

                The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00 and overlaid macro themes to split the tape into opportunity, risk, and resonance tracks:

                ## Macro Themes

                > 1. Computing Power | Policy Support
                > 🧭 Direction: <font color="success">[Bullish]</font>
                > 🎯 Theme Strength: 90 points (High Priority, associated with 14 mapped stocks)
                > 🧠 Interpretation: Policy reinforcement and supply-chain orders pulled capital back into the theme.

                ## Resonance Picks

                <font color="comment">No notice-theme resonance pick met the threshold</font>

                ## Opportunity Board

                > 1. 优博讯 (300531) | 🇨🇳 Stock
                > 🔥 Session Heat: <font color="warning">Main-Theme Tier (Event score 113 points, 2 event clusters / 5 supporting notices)</font>
                > 🧠 Price Action Decode: Continued AI terminal cooperation kept the name firm into the close.

                ## Risk Board

                <font color="comment">No high-priority risk event was detected</font>
                """);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        String markdown = englishService.generateAEveningReportMarkdown(buildFusionContext(), "2026-03-15");

        verify(chatClient, times(2)).prompt(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();

        assertTrue(prompts.get(0).getContents().contains("[Role]"));
        assertTrue(prompts.get(1).getContents().contains("[Retry Requirement]"));
        assertTrue(markdown.contains("Continued AI terminal cooperation kept the name firm into the close."));
        assertTrue(markdown.contains("No high-priority risk event was detected"));
        assertFalse(markdown.contains("产业链订单与政策共振"));
        assertFalse(markdown.contains("暂无高优先级风险事件"));
    }

    @Test
    @DisplayName("Spring 使用的构造器必须显式注入 PushLanguageService")
    void springAutowiredConstructor_ShouldIncludePushLanguageService() {
        Constructor<?>[] constructors = AISummaryServiceImpl.class.getDeclaredConstructors();

        boolean matched = false;
        for (Constructor<?> constructor : constructors) {
            if (constructor.getAnnotation(Autowired.class) == null) {
                continue;
            }
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            matched = parameterTypes.length == 3
                    && parameterTypes[0] == ChatClient.class
                    && parameterTypes[1] == AStockReportClassifier.class
                    && parameterTypes[2] == PushLanguageService.class;
        }

        assertTrue(matched);
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
        context.setMarketSnapshot(buildMarketSnapshot());
        context.setOpportunityInsights(List.of(new AReportOpportunityInsight(
                "000001",
                "平安银行",
                "领军核心",
                "事件评分进入主线级；命中宏观主线共振；支撑公告密集",
                "可作为主线锚点，优先观察开盘承接、量能和主题扩散",
                88,
                true
        )));
        context.setOpportunityAlerts(List.of(buildOpportunityAlert()));
        context.setRiskAlerts(List.of(buildRiskAlert()));
        return context;
    }

    private MarketSnapshot buildMarketSnapshot() {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 15, 8, 55), "TEST");
        snapshot.setMarketState(MarketState.RISK_ON);
        snapshot.setShChangePct(1.36d);
        snapshot.setSzChangePct(1.92d);
        snapshot.setCybChangePct(2.44d);
        snapshot.setUpCount(3620);
        snapshot.setDownCount(1180);
        snapshot.setLimitUpCount(83);
        snapshot.setLimitDownCount(6);
        return snapshot;
    }
}
