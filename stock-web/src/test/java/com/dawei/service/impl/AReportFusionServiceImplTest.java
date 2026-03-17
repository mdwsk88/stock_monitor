package com.dawei.service.impl;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.StockAlertDTO;
import com.dawei.service.MacroNewsService;
import com.dawei.service.StockRankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AReportFusionServiceImplTest {

    @Mock
    private StockRankService stockRankService;
    @Mock
    private MacroNewsService macroNewsService;

    private AReportFusionServiceImpl fusionService;

    @BeforeEach
    void setUp() {
        fusionService = new AReportFusionServiceImpl(
                stockRankService,
                macroNewsService,
                new AStockReportClassifier()
        );
    }

    @Test
    void buildContext_BuildsMacroAndResonanceCards() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 16, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 9, 0);
        when(stockRankService.getATopNStocksWithFrequency(8, start, end))
                .thenReturn(List.of(opportunityAlert(), riskAlert()));
        when(macroNewsService.getShadowThemeEvents(start, end, 12))
                .thenReturn(List.of(mappedMacroTheme(), emptyMacroTheme()));

        AReportFusionContext context = fusionService.buildContext(start, end, 8, 3, 3);

        assertEquals(1, context.getOpportunityAlerts().size());
        assertEquals(1, context.getRiskAlerts().size());
        assertEquals(2, context.getMacroThemes().size());
        assertEquals(1, context.getResonanceCandidates().size());
        assertEquals("000001", context.getResonanceCandidates().get(0).getStockCode());
        assertEquals("算力", context.getResonanceCandidates().get(0).getMacroThemeName());
        assertTrue(context.getResonanceCandidates().get(0).getFusionScore() >= 130);
    }

    @Test
    void buildContext_SkipsConflictingResonance() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 16, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 9, 0);
        when(stockRankService.getATopNStocksWithFrequency(8, start, end))
                .thenReturn(List.of(opportunityAlert()));
        MacroThemeEvent negativeTheme = mappedMacroTheme();
        negativeTheme.setSignalSide("利空");
        when(macroNewsService.getShadowThemeEvents(start, end, 12))
                .thenReturn(List.of(negativeTheme));

        AReportFusionContext context = fusionService.buildContext(start, end, 8, 3, 3);

        assertTrue(context.getResonanceCandidates().isEmpty());
    }

    @Test
    void buildContext_PrefersMappedThemesForDisplayWhenResonanceExists() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 16, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 9, 0);
        when(stockRankService.getATopNStocksWithFrequency(8, start, end))
                .thenReturn(List.of(opportunityAlert()));

        MacroThemeEvent topUnmapped = emptyMacroTheme();
        MacroThemeEvent mappedTheme = mappedMacroTheme();
        mappedTheme.setSignalScore(90);
        when(macroNewsService.getShadowThemeEvents(start, end, 12))
                .thenReturn(List.of(topUnmapped, mappedTheme));

        AReportFusionContext context = fusionService.buildContext(start, end, 8, 1, 3);

        assertEquals(1, context.getMacroThemes().size());
        assertEquals("算力", context.getMacroThemes().get(0).getThemeName());
        assertEquals(1, context.getResonanceCandidates().size());
    }

    @Test
    void buildContext_BuildsResonanceFromMacroEventAlignmentWithoutMappedStocks() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 16, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 9, 0);
        when(stockRankService.getATopNStocksWithFrequency(8, start, end))
                .thenReturn(List.of(mAndAAlert()));

        MacroThemeEvent macroTheme = emptyMacroTheme();
        macroTheme.setThemeName("国企改革");
        macroTheme.setTitle("新一轮国资国企改革重点明晰 有望催生A股并购新机遇");
        macroTheme.setSummary("市场关注央企重组与资产注入。");
        macroTheme.setMappedStocks("");
        macroTheme.setMappedStockCount(0);
        when(macroNewsService.getShadowThemeEvents(start, end, 12))
                .thenReturn(List.of(macroTheme));

        AReportFusionContext context = fusionService.buildContext(start, end, 8, 3, 3);

        assertEquals(1, context.getResonanceCandidates().size());
        assertEquals("600962", context.getResonanceCandidates().get(0).getStockCode());
        assertTrue(context.getResonanceCandidates().get(0).getRelationReason().contains("事件类型共振"));
    }

    @Test
    void buildContext_MergesDuplicateMacroThemesForDisplay() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 16, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 9, 0);
        when(stockRankService.getATopNStocksWithFrequency(8, start, end))
                .thenReturn(List.of(opportunityAlert()));

        MacroThemeEvent financePolicy = financeTheme("金融监管总局支持提振消费专项行动", "政策扶持", 90,
                "中信证券(600030)、东方财富(300059)");
        MacroThemeEvent financeCapital = financeTheme("推动国有大型商业银行补充资本", "政策扶持", 88,
                "中国平安(601318)");
        when(macroNewsService.getShadowThemeEvents(start, end, 12))
                .thenReturn(List.of(financePolicy, financeCapital));

        AReportFusionContext context = fusionService.buildContext(start, end, 8, 3, 3);

        assertEquals(1, context.getMacroThemes().size());
        MacroThemeEvent merged = context.getMacroThemes().get(0);
        assertEquals("金融", merged.getThemeName());
        assertEquals(3, merged.getMappedStockCount());
        assertTrue(merged.getTitle().contains("提振消费"));
        assertTrue(merged.getTitle().contains("补充资本"));
        assertTrue(merged.getMappedStocks().contains("中信证券(600030)"));
        assertTrue(merged.getMappedStocks().contains("中国平安(601318)"));
    }

    private StockAlertDTO<AStockRss> opportunityAlert() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("000001");
        stock.setStockName("平安银行");
        stock.setTitle("平安银行:关于中标10亿元算力项目的公告");
        stock.setEventType("重大合同");
        stock.setSignalSide("利多");
        stock.setAnalysisHint("最高优先级事件为【重大合同】，方向=利多，总评分=118 分");
        return new StockAlertDTO<>(stock, 4, 118, 2, "利多");
    }

    private StockAlertDTO<AStockRss> riskAlert() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("000691");
        stock.setStockName("*ST亚太");
        stock.setTitle("*ST亚太:关于收到立案告知书及退市风险提示的公告");
        stock.setEventType("监管处罚");
        stock.setSignalSide("利空");
        stock.setAnalysisHint("最高优先级事件为【监管处罚】，方向=利空，总评分=112 分");
        return new StockAlertDTO<>(stock, 2, 112, 1, "利空");
    }

    private StockAlertDTO<AStockRss> mAndAAlert() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("600962");
        stock.setStockName("国投中鲁");
        stock.setTitle("国投中鲁:关于发行股份购买资产并募集配套资金暨关联交易的公告");
        stock.setEventType("并购重组");
        stock.setSignalSide("利多");
        stock.setSignalScore(133);
        stock.setAnalysisHint("最高优先级事件为【并购重组】，方向=利多，总评分=133 分");
        return new StockAlertDTO<>(stock, 3, 133, 2, "利多");
    }

    private MacroThemeEvent mappedMacroTheme() {
        MacroThemeEvent theme = new MacroThemeEvent();
        theme.setId("macro-1");
        theme.setThemeName("算力");
        theme.setEventType("政策扶持");
        theme.setSignalSide("利多");
        theme.setSignalScore(90);
        theme.setMappedStockCount(3);
        theme.setMappedStocks("平安银行(000001)、浪潮信息(000977)、中科曙光(603019)");
        theme.setTitle("工信部推进算力基础设施建设");
        theme.setSummary("算力基础设施建设再获政策强化");
        theme.setPubDate(LocalDateTime.of(2026, 3, 16, 8, 30));
        return theme;
    }

    private MacroThemeEvent emptyMacroTheme() {
        MacroThemeEvent theme = new MacroThemeEvent();
        theme.setId("macro-2");
        theme.setThemeName("国家政策");
        theme.setEventType("政策发布");
        theme.setSignalSide("利多");
        theme.setSignalScore(112);
        theme.setMappedStockCount(0);
        theme.setMappedStocks("");
        theme.setTitle("国资国企改革重点明晰");
        theme.setPubDate(LocalDateTime.of(2026, 3, 16, 8, 10));
        return theme;
    }

    private MacroThemeEvent financeTheme(String title, String eventType, int signalScore, String mappedStocks) {
        MacroThemeEvent theme = new MacroThemeEvent();
        theme.setId("macro-" + signalScore + title.hashCode());
        theme.setThemeName("金融");
        theme.setEventType(eventType);
        theme.setSignalSide("利多");
        theme.setSignalScore(signalScore);
        theme.setMappedStocks(mappedStocks);
        theme.setMappedStockCount(mappedStocks.split("、").length);
        theme.setTitle(title);
        theme.setSummary(title);
        theme.setPubDate(LocalDateTime.of(2026, 3, 16, 8, 20));
        return theme;
    }
}
