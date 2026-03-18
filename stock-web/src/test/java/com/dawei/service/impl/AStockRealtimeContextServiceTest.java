package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockRealtimeContextServiceTest {

    @Mock
    private MacroThemeStockRelMapper macroThemeStockRelMapper;

    @Mock
    private MacroThemeEventMapper macroThemeEventMapper;

    @Mock
    private ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;

    private AStockRealtimeContextService contextService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setARealtimeContextHours(24);
        filterConfig.setMacroShadowSignalThreshold(75);
        contextService = new AStockRealtimeContextService(
                macroThemeStockRelMapper,
                macroThemeEventMapper,
                themeAutoPoolCandidateMapper,
                filterConfig
        );
    }

    @Test
    void buildContext_ShouldPreferRecentExplicitRelation() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 18, 13, 40);
        AStockRss notice = buildNotice("300121", "阳谷华泰", "利多", 90);

        MacroThemeStockRel relation = new MacroThemeStockRel();
        relation.setThemeEventId("evt-1");
        relation.setThemeName("低空经济");
        relation.setStockCode("300121");
        relation.setConfidence(96);
        relation.setReason("公司公告直接提到无人机复材");
        relation.setMatchType("EXPLICIT");
        relation.setCreateTime(now.minusMinutes(10));

        MacroThemeEvent event = new MacroThemeEvent();
        event.setId("evt-1");
        event.setThemeName("低空经济");
        event.setTitle("工信部推动低空飞行基础设施建设");
        event.setSummary("政策继续加码");
        event.setSignalSide("利多");
        event.setSignalScore(92);
        event.setPubDate(now.minusMinutes(30));

        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of(relation));
        when(macroThemeEventMapper.selectBatchIds(any())).thenReturn(List.of(event));
        when(themeAutoPoolCandidateMapper.selectList(any())).thenReturn(List.of());

        AStockRealtimeContext context = contextService.buildContext(notice, now);

        assertTrue(context.hasResonance());
        assertEquals("低空经济", context.getThemeName());
        assertEquals("工信部推动低空飞行基础设施建设", context.getMacroTitle());
        assertTrue(context.getResonanceScore() >= 120);
    }

    @Test
    void buildContext_ShouldFallbackToThemeAutoPoolWhenNoExplicitRelation() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 18, 14, 5);
        AStockRss notice = buildNotice("000001", "平安银行", "利多", 87);

        ThemeAutoPoolCandidate candidate = new ThemeAutoPoolCandidate();
        candidate.setThemeName("算力");
        candidate.setStockCode("000001");
        candidate.setCandidateScore(88);
        candidate.setEnabled(1);
        candidate.setReason("算力订单连续命中");
        candidate.setLatestPubDate(now.minusHours(1));

        MacroThemeEvent event = new MacroThemeEvent();
        event.setThemeName("算力");
        event.setTitle("算力基础设施投资持续加码");
        event.setSummary("运营商继续上调资本开支");
        event.setSignalSide("利多");
        event.setSignalScore(86);
        event.setPubDate(now.minusHours(2));

        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of());
        when(themeAutoPoolCandidateMapper.selectList(any())).thenReturn(List.of(candidate));
        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of(event));

        AStockRealtimeContext context = contextService.buildContext(notice, now);

        assertTrue(context.hasResonance());
        assertEquals("算力", context.getThemeName());
        assertEquals("AUTO_POOL", context.getRelationType());
        assertTrue(context.getResonanceScore() >= 100);
    }

    @Test
    void buildContext_ShouldDropConflictingMacroSide() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 18, 10, 30);
        AStockRss notice = buildNotice("600599", "ST熊猫", "利空", 93);

        MacroThemeStockRel relation = new MacroThemeStockRel();
        relation.setThemeEventId("evt-risk");
        relation.setThemeName("低空经济");
        relation.setStockCode("600599");
        relation.setConfidence(92);
        relation.setCreateTime(now.minusMinutes(5));

        MacroThemeEvent event = new MacroThemeEvent();
        event.setId("evt-risk");
        event.setThemeName("低空经济");
        event.setTitle("低空经济扶持政策落地");
        event.setSignalSide("利多");
        event.setSignalScore(90);
        event.setPubDate(now.minusMinutes(20));

        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of(relation));
        when(macroThemeEventMapper.selectBatchIds(any())).thenReturn(List.of(event));
        when(themeAutoPoolCandidateMapper.selectList(any())).thenReturn(List.of());

        AStockRealtimeContext context = contextService.buildContext(notice, now);

        assertFalse(context.hasResonance());
        assertEquals(0, context.getResonanceScore());
    }

    private AStockRss buildNotice(String stockCode, String stockName, String signalSide, int score) {
        AStockRss notice = new AStockRss();
        notice.setStockCode(stockCode);
        notice.setStockName(stockName);
        notice.setSignalSide(signalSide);
        notice.setSignalScore(score);
        return notice;
    }
}
