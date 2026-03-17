package com.dawei.service.impl;

import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.ThemeAutoPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroThemeRelationServiceTest {

    @Mock
    private AStockRssMapper aStockRssMapper;
    @Mock
    private ThemeWatchlistMapper themeWatchlistMapper;
    @Mock
    private ThemeAutoPoolService themeAutoPoolService;

    private MacroThemeRelationService relationService;

    @BeforeEach
    void setUp() {
        relationService = new MacroThemeRelationService(aStockRssMapper, themeWatchlistMapper, themeAutoPoolService);
        lenient().when(aStockRssMapper.selectMaps(any())).thenReturn(List.of());
        lenient().when(themeAutoPoolService.listEnabled()).thenReturn(List.of());
    }

    @Test
    void testBuildRelationsIncludesWatchlistAndExplicitMatches() {
        ThemeWatchlist sameTheme = watchlist("低空经济", "000099", "中信海直", 3);
        ThemeWatchlist explicit = watchlist("军工", "600760", "中航沈飞", 2);

        MacroThemeEvent event = event("event-1", "低空经济");
        MacroNewsRaw raw = raw("工信部推进低空经济试点 中信海直等运营环节受关注");

        List<MacroThemeStockRel> relations = relationService.buildRelations(
                event,
                raw,
                Map.of(
                        "低空经济", List.of(sameTheme),
                        "军工", List.of(explicit)
                )
        );

        assertEquals(2, relations.size());
        assertTrue(relations.stream().anyMatch(rel -> "WATCHLIST".equals(rel.getMatchType())
                && "000099".equals(rel.getStockCode())));
        assertTrue(relations.stream().anyMatch(rel -> "EXPLICIT".equals(rel.getMatchType())
                && "000099".equals(rel.getStockCode())));
    }

    @Test
    void testBuildRelationsMatchesExplicitCodeMention() {
        ThemeWatchlist mapping = watchlist("算力", "000977", "浪潮信息", 3);
        MacroThemeEvent event = event("event-2", "算力");
        MacroNewsRaw raw = raw("工信部支持算力设施建设，相关公司包括 000977 等");

        List<MacroThemeStockRel> relations = relationService.buildRelations(
                event,
                raw,
                Map.of("算力", List.of(mapping))
        );

        assertEquals(2, relations.size());
        assertTrue(relations.stream().anyMatch(rel -> "EXPLICIT".equals(rel.getMatchType())
                && "标题/摘要显式提及股票代码".equals(rel.getReason())));
    }

    @Test
    void testExplicitRelationUsesEventThemeNameInsteadOfWatchlistTheme() {
        ThemeWatchlist explicit = watchlist("军工", "600760", "中航沈飞", 2);
        MacroThemeEvent event = event("event-2b", "低空经济");
        MacroNewsRaw raw = raw("低空经济试点推进，中航沈飞参与相关飞行平台测试");

        List<MacroThemeStockRel> relations = relationService.buildRelations(
                event,
                raw,
                Map.of("军工", List.of(explicit))
        );

        MacroThemeStockRel explicitRelation = relations.stream()
                .filter(rel -> "EXPLICIT".equals(rel.getMatchType()))
                .findFirst()
                .orElseThrow();
        assertEquals("低空经济", explicitRelation.getThemeName());
        assertEquals("600760", explicitRelation.getStockCode());
    }

    @Test
    void testBuildRelationsSkipsAmbiguousThemeNameStock() {
        ThemeWatchlist mapping = watchlist("机器人", "300024", "机器人", 3);
        MacroThemeEvent event = event("event-3", "机器人");
        MacroNewsRaw raw = raw("人形机器人产业链继续升温");

        List<MacroThemeStockRel> relations = relationService.buildRelations(
                event,
                raw,
                Map.of("机器人", List.of(mapping))
        );

        assertEquals(1, relations.size());
        assertTrue(relations.stream().allMatch(rel -> "WATCHLIST".equals(rel.getMatchType())));
    }

    @Test
    void testBuildRelationsSkipsMarketDigestNameMatch() {
        ThemeWatchlist mapping = watchlist("锂电", "300750", "宁德时代", 3);
        MacroThemeEvent event = event("event-4", "锂电");
        MacroNewsRaw raw = raw("港股科技ETF午后拉升，宁德时代等权重股受关注");

        List<MacroThemeStockRel> relations = relationService.buildRelations(
                event,
                raw,
                Map.of("锂电", List.of(mapping))
        );

        assertEquals(1, relations.size());
        assertTrue(relations.stream().allMatch(rel -> "WATCHLIST".equals(rel.getMatchType())));
    }

    @Test
    void testLoadEnabledWatchlistDelegatesToMapper() {
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of(watchlist("算力", "000977", "浪潮信息", 3)));

        Map<String, List<ThemeWatchlist>> result = relationService.loadEnabledWatchlist();

        assertEquals(1, result.size());
        assertEquals(1, result.get("算力").size());
    }

    @Test
    void testLoadEnabledWatchlistIncludesAutoPoolCandidates() {
        ThemeAutoPoolCandidate autoCandidate = new ThemeAutoPoolCandidate();
        autoCandidate.setId("auto-1");
        autoCandidate.setThemeName("金融");
        autoCandidate.setStockCode("600030");
        autoCandidate.setStockName("中信证券");
        autoCandidate.setCandidateScore(95);
        autoCandidate.setEnabled(1);

        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(themeAutoPoolService.listEnabled()).thenReturn(List.of(autoCandidate));

        Map<String, List<ThemeWatchlist>> result = relationService.loadEnabledWatchlist();

        assertEquals(1, result.size());
        assertEquals(1, result.get("金融").size());
        assertEquals("600030", result.get("金融").get(0).getStockCode());
        assertEquals(3, result.get("金融").get(0).getPriority());
    }

    @Test
    void testLoadExplicitReferencePoolIncludesAStockRssNames() {
        when(aStockRssMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("stock_code", "300308", "stock_name", "中际旭创")
        ));

        List<MacroThemeRelationService.StockReference> pool = relationService.loadExplicitReferencePool(Map.of());

        assertTrue(pool.stream().anyMatch(ref -> "300308".equals(ref.stockCode())
                && "中际旭创".equals(ref.stockName())
                && !ref.fromWatchlist()));
    }

    private ThemeWatchlist watchlist(String themeName, String stockCode, String stockName, int priority) {
        ThemeWatchlist watchlist = new ThemeWatchlist();
        watchlist.setThemeName(themeName);
        watchlist.setStockCode(stockCode);
        watchlist.setStockName(stockName);
        watchlist.setPriority(priority);
        watchlist.setEnabled(1);
        return watchlist;
    }

    private MacroThemeEvent event(String id, String themeName) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId(id);
        event.setThemeName(themeName);
        event.setTitle("政策支持");
        event.setSummary("支持相关产业发展");
        event.setPubDate(LocalDateTime.of(2026, 3, 16, 10, 0));
        return event;
    }

    private MacroNewsRaw raw(String title) {
        MacroNewsRaw raw = new MacroNewsRaw();
        raw.setTitle(title);
        raw.setContent(title);
        return raw;
    }
}
