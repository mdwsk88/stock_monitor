package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.MacroNewsRawMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.ThemeAutoPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroNewsHistoryRepairServiceTest {

    @Mock
    private MacroNewsRawMapper macroNewsRawMapper;
    @Mock
    private MacroThemeEventMapper macroThemeEventMapper;
    @Mock
    private MacroThemeStockRelMapper macroThemeStockRelMapper;
    @Mock
    private AStockRssMapper aStockRssMapper;
    @Mock
    private ThemeWatchlistMapper themeWatchlistMapper;
    @Mock
    private ThemeAutoPoolService themeAutoPoolService;

    private MacroNewsHistoryRepairService repairService;

    @BeforeEach
    void setUp() {
        StockFilterConfig config = new StockFilterConfig();
        MacroNewsSignalService signalService = new MacroNewsSignalService(config);
        MacroThemeRelationService relationService = new MacroThemeRelationService(aStockRssMapper, themeWatchlistMapper, themeAutoPoolService);
        repairService = new MacroNewsHistoryRepairService(
                macroNewsRawMapper,
                macroThemeEventMapper,
                macroThemeStockRelMapper,
                relationService,
                signalService,
                themeAutoPoolService
        );
        when(aStockRssMapper.selectMaps(any())).thenReturn(List.of());
        when(themeAutoPoolService.listEnabled()).thenReturn(List.of());
        when(themeAutoPoolService.rebuildFromRecentExplicitRelations(anyInt()))
                .thenReturn(new ThemeAutoPoolService.RebuildSummary(0, 72));
    }

    @Test
    void testRepairRecentEventsRewritesThemeAndRebuildsRelations() {
        MacroNewsRaw raw = raw("中国政府网", "OFFICIAL", "policy-1",
                "国务院办公厅关于促进低空经济高质量发展的意见",
                "国务院办公厅印发意见，提出若干措施支持低空经济发展。");
        MacroThemeEvent existing = event("event-1", "policy-1", "国家政策", "政策发布", "利多", 112);
        ThemeWatchlist mapping = watchlist("低空经济", "000099", "中信海直");

        when(macroNewsRawMapper.selectList(any())).thenReturn(List.of(raw));
        when(macroThemeEventMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of(mapping));
        when(macroThemeStockRelMapper.delete(any())).thenReturn(1);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);

        MacroNewsHistoryRepairService.RepairSummary summary = repairService.repairRecentEvents(72);

        ArgumentCaptor<MacroThemeEvent> eventCaptor = ArgumentCaptor.forClass(MacroThemeEvent.class);
        verify(macroThemeEventMapper).updateById(eventCaptor.capture());
        MacroThemeEvent updated = eventCaptor.getValue();

        assertEquals("低空经济", updated.getThemeName());
        assertEquals("政策扶持", updated.getEventType());
        assertEquals(1, summary.getUpdatedEvents());
        assertEquals(1, summary.getDeletedRelations());
        assertEquals(1, summary.getInsertedRelations());
        assertEquals(0, summary.getAutoPoolInserted());
    }

    @Test
    void testRepairRecentEventsDeletesNoLongerQualifiedEvents() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK", "quick-1",
                "华泰证券：建议持续关注推理侧算力与Agent应用机会",
                "华泰证券指出，建议持续关注推理侧算力、平台型基础设施以及Agent应用机会。");
        MacroThemeEvent existing = event("event-2", "quick-1", "算力", "产业观察", "中性", 78);

        when(macroNewsRawMapper.selectList(any())).thenReturn(List.of(raw));
        when(macroThemeEventMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroThemeStockRelMapper.delete(any())).thenReturn(2);

        MacroNewsHistoryRepairService.RepairSummary summary = repairService.repairRecentEvents(48);

        verify(macroThemeEventMapper).deleteById("event-2");
        assertEquals(1, summary.getFiltered());
        assertEquals(1, summary.getDeletedEvents());
        assertEquals(2, summary.getDeletedRelations());
    }

    @Test
    void testRepairRecentEventsRewritesTradeFrictionToExternalRisk() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK", "trade-1",
                "商务部新闻发言人就美贸易代表办公室宣布对包括中国在内的60个经济体发起301调查答记者问",
                "美贸易代表办公室发起301调查，中方表示坚决反对将经贸问题政治化。");
        MacroThemeEvent existing = event("event-3", "trade-1", "行业催化", "政策扶持", "利多", 82);

        when(macroNewsRawMapper.selectList(any())).thenReturn(List.of(raw));
        when(macroThemeEventMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroThemeStockRelMapper.delete(any())).thenReturn(0);

        MacroNewsHistoryRepairService.RepairSummary summary = repairService.repairRecentEvents(48);

        ArgumentCaptor<MacroThemeEvent> eventCaptor = ArgumentCaptor.forClass(MacroThemeEvent.class);
        verify(macroThemeEventMapper).updateById(eventCaptor.capture());
        MacroThemeEvent updated = eventCaptor.getValue();

        assertEquals("外部风险", updated.getThemeName());
        assertEquals("贸易摩擦", updated.getEventType());
        assertEquals("利空", updated.getSignalSide());
        assertEquals(1, summary.getUpdatedEvents());
    }

    @Test
    void testRepairRecentEventsBuildsExplicitRelations() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK", "quick-2",
                "工信部推进低空经济试点，中信海直等运营环节受关注",
                "低空经济基础设施建设提速，中信海直等运营环节受关注。");
        MacroThemeEvent existing = event("event-4", "quick-2", "低空经济", "产业观察", "中性", 78);
        ThemeWatchlist mapping = watchlist("低空经济", "000099", "中信海直");

        when(macroNewsRawMapper.selectList(any())).thenReturn(List.of(raw));
        when(macroThemeEventMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of(mapping));
        when(macroThemeStockRelMapper.delete(any())).thenReturn(0);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);

        MacroNewsHistoryRepairService.RepairSummary summary = repairService.repairRecentEvents(48);

        assertEquals(2, summary.getInsertedRelations());
    }

    @Test
    void testRepairRecentEventsReplaysRelationsAfterAutoPoolRebuild() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK", "quick-3",
                "工信部推进低空经济试点，中信海直等运营环节受关注",
                "低空经济基础设施建设提速，中信海直等运营环节受关注。");
        MacroThemeEvent existing = event("event-5", "quick-3", "低空经济", "产业观察", "中性", 78);
        ThemeAutoPoolCandidate autoCandidate = new ThemeAutoPoolCandidate();
        autoCandidate.setId("auto-1");
        autoCandidate.setThemeName("低空经济");
        autoCandidate.setStockCode("000099");
        autoCandidate.setStockName("中信海直");
        autoCandidate.setCandidateScore(85);
        autoCandidate.setEnabled(1);

        when(macroNewsRawMapper.selectList(any())).thenReturn(List.of(raw));
        when(macroThemeEventMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(themeAutoPoolService.listEnabled()).thenReturn(List.of(), List.of(autoCandidate));
        ThemeAutoPoolService.RebuildSummary rebuildSummary = new ThemeAutoPoolService.RebuildSummary(1, 48);
        rebuildSummary.incrementInserted();
        rebuildSummary.incrementEnabled();
        when(themeAutoPoolService.rebuildFromRecentExplicitRelations(anyInt())).thenReturn(rebuildSummary);
        when(macroThemeStockRelMapper.delete(any())).thenReturn(0);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);

        MacroNewsHistoryRepairService.RepairSummary summary = repairService.repairRecentEvents(48);

        assertEquals(1, summary.getAutoPoolInserted());
        assertEquals(1, summary.getAutoPoolEnabled());
        assertEquals(2, summary.getReplayedRelations());
        verify(macroThemeStockRelMapper, times(2)).insertIgnore(any());
    }

    private MacroNewsRaw raw(String sourceName, String sourceType, String newsKey, String title, String content) {
        MacroNewsRaw raw = new MacroNewsRaw();
        raw.setId("raw-" + newsKey);
        raw.setSourceName(sourceName);
        raw.setSourceType(sourceType);
        raw.setNewsKey(newsKey);
        raw.setTitle(title);
        raw.setContent(content);
        raw.setLink("https://example.com/" + newsKey);
        raw.setPubDate(LocalDateTime.of(2026, 3, 16, 7, 30));
        raw.setCreateTime(LocalDateTime.of(2026, 3, 16, 7, 31));
        return raw;
    }

    private MacroThemeEvent event(String id, String newsKey, String themeName, String eventType, String side, int score) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId(id);
        event.setSourceName("中国政府网");
        event.setSourceType("OFFICIAL");
        event.setNewsKey(newsKey);
        event.setTitle("old");
        event.setSummary("old");
        event.setLink("https://old.example.com");
        event.setSourceTags("old");
        event.setPubDate(LocalDateTime.of(2026, 3, 16, 7, 0));
        event.setCreateTime(LocalDateTime.of(2026, 3, 16, 7, 0));
        event.setThemeName(themeName);
        event.setEventType(eventType);
        event.setSignalSide(side);
        event.setSignalScore(score);
        event.setImportanceLevel(4);
        event.setClusterKey("old-cluster");
        return event;
    }

    private ThemeWatchlist watchlist(String themeName, String stockCode, String stockName) {
        ThemeWatchlist watchlist = new ThemeWatchlist();
        watchlist.setId("watch-1");
        watchlist.setThemeName(themeName);
        watchlist.setStockCode(stockCode);
        watchlist.setStockName(stockName);
        watchlist.setPriority(2);
        watchlist.setEnabled(1);
        watchlist.setReason("核心观察池");
        watchlist.setCreateTime(LocalDateTime.of(2026, 3, 16, 7, 0));
        return watchlist;
    }
}
