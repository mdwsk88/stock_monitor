package com.dawei.service.impl;

import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.ThemeWatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeWatchlistServiceImplTest {

    @Mock
    private ThemeWatchlistMapper themeWatchlistMapper;

    private ThemeWatchlistServiceImpl themeWatchlistService;

    @BeforeEach
    void setUp() {
        themeWatchlistService = new ThemeWatchlistServiceImpl(themeWatchlistMapper);
    }

    @Test
    void testSeedDefaultsInsertsWhenWatchlistIsEmpty() {
        when(themeWatchlistMapper.selectOne(any())).thenReturn(null);
        when(themeWatchlistMapper.insert(any(ThemeWatchlist.class))).thenReturn(1);

        ThemeWatchlistService.SeedSummary summary = themeWatchlistService.seedDefaults(false);

        assertEquals(summary.getTotalTemplates(), summary.getInserted());
        assertEquals(0, summary.getUpdated());
        assertEquals(0, summary.getSkipped());
        verify(themeWatchlistMapper, times(summary.getTotalTemplates())).insert(any(ThemeWatchlist.class));
    }

    @Test
    void testUpsertUpdatesExistingRecord() {
        ThemeWatchlist existing = new ThemeWatchlist();
        existing.setId("watch-1");
        existing.setThemeName("低空经济");
        existing.setStockCode("000099");
        existing.setStockName("旧名称");
        existing.setPriority(1);
        existing.setEnabled(0);
        existing.setReason("旧原因");
        when(themeWatchlistMapper.selectOne(any())).thenReturn(existing);
        when(themeWatchlistMapper.updateById(existing)).thenReturn(1);

        ThemeWatchlist input = new ThemeWatchlist();
        input.setThemeName("低空经济");
        input.setStockCode("000099");
        input.setStockName("中信海直");
        input.setPriority(3);
        input.setEnabled(1);
        input.setReason("核心观察池");

        ThemeWatchlist saved = themeWatchlistService.upsert(input);

        assertSame(existing, saved);
        assertEquals("中信海直", saved.getStockName());
        assertEquals(3, saved.getPriority());
        assertEquals(1, saved.getEnabled());
        assertEquals("核心观察池", saved.getReason());
        verify(themeWatchlistMapper).updateById(existing);
    }

    @Test
    void testUpsertInsertsNewRecord() {
        when(themeWatchlistMapper.selectOne(any())).thenReturn(null);
        when(themeWatchlistMapper.insert(any(ThemeWatchlist.class))).thenReturn(1);

        ThemeWatchlist input = new ThemeWatchlist();
        input.setThemeName("算力");
        input.setStockCode("000977");
        input.setStockName("浪潮信息");
        input.setPriority(3);
        input.setEnabled(1);
        input.setReason("默认种子");

        ThemeWatchlist saved = themeWatchlistService.upsert(input);

        ArgumentCaptor<ThemeWatchlist> captor = ArgumentCaptor.forClass(ThemeWatchlist.class);
        verify(themeWatchlistMapper).insert(captor.capture());
        ThemeWatchlist inserted = captor.getValue();
        assertNotNull(inserted.getId());
        assertEquals("算力", inserted.getThemeName());
        assertEquals("000977", inserted.getStockCode());
        assertEquals("浪潮信息", inserted.getStockName());
        assertEquals(inserted, saved);
    }

    @Test
    void testListDelegatesToMapper() {
        List<ThemeWatchlist> expected = List.of(new ThemeWatchlist(), new ThemeWatchlist());
        when(themeWatchlistMapper.selectList(any())).thenReturn(expected);

        List<ThemeWatchlist> actual = themeWatchlistService.list("低空经济", 1);

        assertSame(expected, actual);
        verify(themeWatchlistMapper).selectList(any());
    }
}
