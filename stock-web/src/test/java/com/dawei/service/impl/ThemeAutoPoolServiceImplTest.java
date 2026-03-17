package com.dawei.service.impl;

import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeAutoPoolServiceImplTest {

    @Mock
    private ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;
    @Mock
    private MacroThemeStockRelMapper macroThemeStockRelMapper;

    private ThemeAutoPoolServiceImpl themeAutoPoolService;

    @BeforeEach
    void setUp() {
        themeAutoPoolService = new ThemeAutoPoolServiceImpl(themeAutoPoolCandidateMapper, macroThemeStockRelMapper);
    }

    @Test
    void testRecordExplicitHitEnablesCandidateOnFirstHit() {
        when(themeAutoPoolCandidateMapper.selectOne(any())).thenReturn(null);

        ThemeAutoPoolCandidate saved = themeAutoPoolService.recordExplicitHit(
                "算力",
                "000977",
                "浪潮信息",
                85,
                "标题/摘要显式提及股票名称",
                LocalDateTime.of(2026, 3, 16, 19, 30)
        );

        ArgumentCaptor<ThemeAutoPoolCandidate> captor = ArgumentCaptor.forClass(ThemeAutoPoolCandidate.class);
        verify(themeAutoPoolCandidateMapper).insert(captor.capture());
        ThemeAutoPoolCandidate inserted = captor.getValue();
        assertNotNull(inserted.getId());
        assertEquals("算力", inserted.getThemeName());
        assertEquals("000977", inserted.getStockCode());
        assertEquals(1, inserted.getHitCount());
        assertEquals(85, inserted.getCandidateScore());
        assertEquals(1, inserted.getEnabled());
        assertSame(inserted, saved);
    }

    @Test
    void testRecordExplicitHitEnablesCandidateOnSecondHit() {
        ThemeAutoPoolCandidate existing = new ThemeAutoPoolCandidate();
        existing.setId("auto-1");
        existing.setThemeName("算力");
        existing.setStockCode("000977");
        existing.setStockName("浪潮信息");
        existing.setHitCount(1);
        existing.setCandidateScore(85);
        existing.setEnabled(0);
        existing.setLatestPubDate(LocalDateTime.of(2026, 3, 16, 18, 0));
        when(themeAutoPoolCandidateMapper.selectOne(any())).thenReturn(existing);

        ThemeAutoPoolCandidate saved = themeAutoPoolService.recordExplicitHit(
                "算力",
                "000977",
                "浪潮信息",
                85,
                "标题/摘要显式提及股票名称",
                LocalDateTime.of(2026, 3, 16, 19, 30)
        );

        assertEquals(2, saved.getHitCount());
        assertEquals(90, saved.getCandidateScore());
        assertEquals(1, saved.getEnabled());
        verify(themeAutoPoolCandidateMapper).updateById(existing);
    }

    @Test
    void testRebuildFromRecentExplicitRelationsRewritesAutoPool() {
        MacroThemeStockRel relationOne = relation("低空经济", "000099", "中信海直", 85,
                LocalDateTime.of(2026, 3, 16, 10, 0));
        MacroThemeStockRel relationTwo = relation("低空经济", "000099", "中信海直", 88,
                LocalDateTime.of(2026, 3, 16, 11, 0));
        MacroThemeStockRel relationThree = relation("金融", "600030", "中信证券", 92,
                LocalDateTime.of(2026, 3, 16, 12, 0));
        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of(relationOne, relationTwo, relationThree));

        com.dawei.service.ThemeAutoPoolService.RebuildSummary summary =
                themeAutoPoolService.rebuildFromRecentExplicitRelations(72);

        verify(themeAutoPoolCandidateMapper).delete(any());
        verify(themeAutoPoolCandidateMapper, times(2)).insert(any(ThemeAutoPoolCandidate.class));
        assertEquals(3, summary.getScanned());
        assertEquals(2, summary.getInserted());
        assertEquals(2, summary.getEnabled());
    }

    private MacroThemeStockRel relation(String themeName,
                                        String stockCode,
                                        String stockName,
                                        int confidence,
                                        LocalDateTime createTime) {
        MacroThemeStockRel relation = new MacroThemeStockRel();
        relation.setThemeName(themeName);
        relation.setStockCode(stockCode);
        relation.setStockName(stockName);
        relation.setConfidence(confidence);
        relation.setMatchType("EXPLICIT");
        relation.setReason("标题/摘要显式提及股票名称");
        relation.setCreateTime(createTime);
        return relation;
    }
}
