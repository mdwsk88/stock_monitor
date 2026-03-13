package com.dawei.service.impl;

import com.dawei.entity.AStockRss;
import com.dawei.entity.USStockRss;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.USStockRssMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock
    private USStockRssMapper usStockRssMapper;

    @Mock
    private AStockRssMapper aStockRssMapper;

    @InjectMocks
    private StockServiceImpl stockService;

    @Test
    void saveStockNewsIfAbsent_GeneratesIdAndReturnsTrue() {
        USStockRss stockNews = new USStockRss();
        stockNews.setStockCode("AAPL");
        stockNews.setLink("https://example.com/aapl");

        when(usStockRssMapper.insertIgnore(any(USStockRss.class))).thenReturn(1);

        boolean inserted = stockService.saveStockNewsIfAbsent(stockNews);

        ArgumentCaptor<USStockRss> captor = ArgumentCaptor.forClass(USStockRss.class);
        verify(usStockRssMapper).insertIgnore(captor.capture());

        assertTrue(inserted);
        assertNotNull(captor.getValue().getId());
        assertFalse(captor.getValue().getId().isBlank());
    }

    @Test
    void saveAStockNewsIfAbsent_ReturnsFalseWhenDuplicate() {
        AStockRss stockNews = new AStockRss();
        stockNews.setStockCode("000001");
        stockNews.setTitle("关于xxx的公告");

        when(aStockRssMapper.insertIgnore(any(AStockRss.class))).thenReturn(0);

        boolean inserted = stockService.saveAStockNewsIfAbsent(stockNews);

        ArgumentCaptor<AStockRss> captor = ArgumentCaptor.forClass(AStockRss.class);
        verify(aStockRssMapper).insertIgnore(captor.capture());

        assertFalse(inserted);
        assertNotNull(captor.getValue().getId());
        assertFalse(captor.getValue().getId().isBlank());
    }
}
