package com.dawei.controller;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.service.AReportFusionService;
import com.dawei.service.AISummaryService;
import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import com.dawei.service.ThemeWatchlistService;
import com.dawei.service.impl.MacroNewsHistoryRepairService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroNewsControllerTest {

    @Mock
    private MacroNewsService macroNewsService;
    @Mock
    private AReportFusionService aReportFusionService;
    @Mock
    private AISummaryService aiSummaryService;
    @Mock
    private ThemeWatchlistService themeWatchlistService;
    @Mock
    private ThemeAutoPoolService themeAutoPoolService;
    @Mock
    private MacroNewsHistoryRepairService macroNewsHistoryRepairService;

    @InjectMocks
    private MacroNewsController macroNewsController;

    @Test
    @DisplayName("测试获取主题观察池列表")
    void testGetWatchlist() {
        ThemeWatchlist mapping = new ThemeWatchlist();
        mapping.setThemeName("低空经济");
        mapping.setStockCode("000099");
        when(themeWatchlistService.list("低空经济", 1)).thenReturn(List.of(mapping));

        Map<String, Object> result = macroNewsController.getWatchlist("低空经济", "true");

        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        List<ThemeWatchlist> watchlist = (List<ThemeWatchlist>) result.get("watchlist");
        assertEquals(1, watchlist.size());
        assertSame(mapping, watchlist.get(0));
    }

    @Test
    @DisplayName("测试获取自动候选池列表")
    void testGetAutoPool() {
        ThemeAutoPoolCandidate candidate = new ThemeAutoPoolCandidate();
        candidate.setThemeName("金融");
        candidate.setStockCode("600030");
        when(themeAutoPoolService.list("金融", 1)).thenReturn(List.of(candidate));

        Map<String, Object> result = macroNewsController.getAutoPool("金融", "1");

        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        List<ThemeAutoPoolCandidate> autoPool = (List<ThemeAutoPoolCandidate>) result.get("autoPool");
        assertSame(candidate, autoPool.get(0));
    }

    @Test
    @DisplayName("测试写入默认主题观察池并触发修复")
    void testSeedDefaultWatchlist() {
        ThemeWatchlistService.SeedSummary seedSummary = new ThemeWatchlistService.SeedSummary(36);
        seedSummary.incrementInserted();
        MacroNewsHistoryRepairService.RepairSummary repairSummary =
                new MacroNewsHistoryRepairService.RepairSummary(20, 72);
        when(themeWatchlistService.seedDefaults(false)).thenReturn(seedSummary);
        when(macroNewsHistoryRepairService.repairRecentEvents(72)).thenReturn(repairSummary);

        Map<String, Object> result = macroNewsController.seedDefaultWatchlist(false, 72);

        assertTrue((Boolean) result.get("success"));
        assertEquals("默认主题观察池已写入", result.get("message"));
        assertSame(seedSummary, result.get("seedSummary"));
        assertSame(repairSummary, result.get("repairSummary"));
        verify(themeWatchlistService).seedDefaults(false);
        verify(macroNewsHistoryRepairService).repairRecentEvents(72);
    }

    @Test
    @DisplayName("测试保存单条主题观察池")
    void testUpsertWatchlist() {
        ThemeWatchlist saved = new ThemeWatchlist();
        saved.setThemeName("算力");
        saved.setStockCode("000977");
        when(themeWatchlistService.upsert(any())).thenReturn(saved);

        MacroNewsController.WatchlistUpsertRequest request = new MacroNewsController.WatchlistUpsertRequest();
        request.setThemeName("算力");
        request.setStockCode("000977");
        request.setStockName("浪潮信息");

        Map<String, Object> result = macroNewsController.upsertWatchlist(request);

        assertTrue((Boolean) result.get("success"));
        assertEquals("主题观察池已保存", result.get("message"));
        assertSame(saved, result.get("watchlist"));
        verify(themeWatchlistService).upsert(any());
    }

    @Test
    @DisplayName("测试更新主题观察池启用状态")
    void testUpdateWatchlistEnabled() {
        when(themeWatchlistService.updateEnabled("watch-1", true)).thenReturn(true);

        Map<String, Object> result = macroNewsController.updateWatchlistEnabled("watch-1", true);

        assertTrue((Boolean) result.get("success"));
        assertEquals("主题观察池状态已更新", result.get("message"));
        verify(themeWatchlistService).updateEnabled("watch-1", true);
    }

    @Test
    @DisplayName("测试删除主题观察池")
    void testDeleteWatchlist() {
        when(themeWatchlistService.deleteById("watch-1")).thenReturn(true);

        Map<String, Object> result = macroNewsController.deleteWatchlist("watch-1");

        assertTrue((Boolean) result.get("success"));
        assertEquals("主题观察池记录已删除", result.get("message"));
        verify(themeWatchlistService).deleteById("watch-1");
    }

    @Test
    @DisplayName("测试获取融合预览")
    void testGetFusionPreview() {
        AReportFusionContext context = new AReportFusionContext();
        when(aReportFusionService.buildContext(any(), any(), eq(8), eq(3), eq(3))).thenReturn(context);

        Map<String, Object> result = macroNewsController.getFusionPreview(24, 8, 3, 3);

        assertTrue((Boolean) result.get("success"));
        assertSame(context, result.get("context"));
        assertEquals(0, result.get("macroThemeCount"));
        assertEquals(0, result.get("resonanceCount"));
        verify(aReportFusionService).buildContext(any(), any(), eq(8), eq(3), eq(3));
    }

    @Test
    @DisplayName("测试生成晨报预览")
    void testGetMorningReportPreview() {
        AReportFusionContext context = new AReportFusionContext();
        when(aReportFusionService.buildContext(any(), any(), eq(8), eq(3), eq(3))).thenReturn(context);
        when(aiSummaryService.generateAMorningReportMarkdown(context, "2026-03-16"))
                .thenReturn("# 晨报预览");

        Map<String, Object> result = macroNewsController.getReportPreview(
                "morning",
                24,
                8,
                3,
                3,
                "2026-03-16"
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals("morning", result.get("reportType"));
        assertEquals("2026-03-16", result.get("reportDate"));
        assertEquals("# 晨报预览", result.get("markdown"));
        verify(aReportFusionService).buildContext(any(), any(), eq(8), eq(3), eq(3));
        verify(aiSummaryService).generateAMorningReportMarkdown(context, "2026-03-16");
    }

    @Test
    @DisplayName("测试生成晚报预览")
    void testGetEveningReportPreview() {
        AReportFusionContext context = new AReportFusionContext();
        when(aReportFusionService.buildContext(any(), any(), eq(8), eq(3), eq(3))).thenReturn(context);
        when(aiSummaryService.generateAEveningReportMarkdown(context, "2026-03-16"))
                .thenReturn("# 晚报预览");

        Map<String, Object> result = macroNewsController.getReportPreview(
                "evening",
                24,
                8,
                3,
                3,
                "2026-03-16"
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals("evening", result.get("reportType"));
        assertEquals("# 晚报预览", result.get("markdown"));
        verify(aiSummaryService).generateAEveningReportMarkdown(context, "2026-03-16");
    }
}
