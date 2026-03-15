package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import com.dawei.mapper.AStockRssMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockHistoryRepairServiceTest {

    @Mock
    private AStockRssMapper aStockRssMapper;

    private AStockHistoryRepairService repairService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        AStockSignalService signalService = new AStockSignalService(filterConfig);
        repairService = new AStockHistoryRepairService(aStockRssMapper, signalService);
    }

    @Test
    void repairHistoricalNotices_DeletesNonPreferredCodeAndBackfillsFields() {
        AStockRss bond = new AStockRss();
        bond.setId("bond-id");
        bond.setStockCode("113666");
        bond.setStockName("爱玛转债");
        bond.setTitle("爱玛科技:爱玛科技关于部分限制性股票回购注销完成调整“爱玛转债”转股价格的公告");
        bond.setLink("https://data.eastmoney.com/notices/detail/113666/ANN_BOND.html");
        bond.setPubDate(LocalDateTime.of(2026, 3, 15, 18, 0));

        AStockRss equity = new AStockRss();
        equity.setId("equity-id");
        equity.setStockCode("002266");
        equity.setStockName(null);
        equity.setTitle("浙富控股:工程中标公告");
        equity.setTag("重大合同");
        equity.setLink("https://data.eastmoney.com/notices/detail/002266/ANN_EQUITY.html");
        equity.setPubDate(LocalDateTime.of(2026, 3, 15, 16, 30));

        when(aStockRssMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(bond, equity));
        when(aStockRssMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        AStockHistoryRepairService.RepairSummary summary = repairService.repairHistoricalNotices();

        assertEquals(2, summary.getScanned());
        assertEquals(1, summary.getDeletedNonEquity());
        assertEquals(1, summary.getUpdated());
        assertEquals(1, summary.getBackfilledArtCode());
        assertEquals(1, summary.getBackfilledStockName());

        verify(aStockRssMapper).deleteById("bond-id");

        ArgumentCaptor<AStockRss> updatedCaptor = ArgumentCaptor.forClass(AStockRss.class);
        verify(aStockRssMapper).updateById(updatedCaptor.capture());
        AStockRss updated = updatedCaptor.getValue();
        assertEquals("ANN_EQUITY", updated.getArtCode());
        assertEquals("浙富控股", updated.getStockName());
        assertEquals("重大合同", updated.getEventType());
        assertEquals("利多", updated.getSignalSide());
        assertNotNull(updated.getSignalScore());
        assertTrue(updated.getSignalScore() >= 60);
        assertTrue(updated.getClusterKey().startsWith("重大合同|"));
    }

    @Test
    void repairHistoricalNotices_DeletesDuplicateAfterBackfillingArtCode() {
        AStockRss duplicate = new AStockRss();
        duplicate.setId("dup-id");
        duplicate.setStockCode("300001");
        duplicate.setStockName("特锐德");
        duplicate.setTitle("特锐德:关于中标青海油田风电项目的提示性公告");
        duplicate.setTag("重大合同");
        duplicate.setLink("https://data.eastmoney.com/notices/detail/300001/ANN_DUP.html");
        duplicate.setPubDate(LocalDateTime.of(2026, 3, 15, 19, 16));

        when(aStockRssMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(duplicate));
        when(aStockRssMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        AStockHistoryRepairService.RepairSummary summary = repairService.repairHistoricalNotices();

        assertEquals(1, summary.getScanned());
        assertEquals(1, summary.getDeletedDuplicate());
        assertEquals(1, summary.getBackfilledArtCode());

        verify(aStockRssMapper).deleteById("dup-id");
        verify(aStockRssMapper, never()).updateById(any(AStockRss.class));
    }
}
