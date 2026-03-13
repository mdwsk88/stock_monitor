package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import com.dawei.entity.AStockRssAggregate;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.entity.USStockRssAggregate;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.USStockRssMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRankServiceImplTest {

    @Mock
    private USStockRssMapper usStockRssMapper;

    @Mock
    private AStockRssMapper aStockRssMapper;

    private StockFilterConfig filterConfig;

    @InjectMocks
    private StockRankServiceImpl stockRankService;

    @BeforeEach
    void setUp() {
        filterConfig = new StockFilterConfig();
        filterConfig.setFrequencyThreshold(3);
        stockRankService = new StockRankServiceImpl(usStockRssMapper, aStockRssMapper, filterConfig);
    }

    @Test
    void getUSTopNStocksWithFrequency_UsesSqlAggregationAndEnrichesTitles() {
        USStockRssAggregate aggregate = new USStockRssAggregate();
        aggregate.setStockCode("NVDA");
        aggregate.setTitle("Nvidia earnings beat");
        aggregate.setTitleZh("英伟达业绩超预期");
        aggregate.setFrequency(12);

        USStockRss latest = new USStockRss();
        latest.setStockCode("NVDA");
        latest.setTitle("Nvidia earnings beat");
        latest.setTitleZh("英伟达业绩超预期");

        USStockRss another = new USStockRss();
        another.setStockCode("NVDA");
        another.setTitle("Nvidia expands AI capacity");
        another.setTitleZh("英伟达扩大AI产能");

        when(usStockRssMapper.selectTopStocksByFrequency(anyString(), anyString(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of(aggregate));
        when(usStockRssMapper.selectFilteredByStockCodes(anyList(), anyString(), anyString(), anyList()))
                .thenReturn(List.of(latest, another, latest));

        List<StockAlertDTO<USStockRss>> result = stockRankService.getUSTopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 13, 8, 0),
                LocalDateTime.of(2026, 3, 13, 20, 0)
        );

        assertEquals(1, result.size());
        assertEquals(12, result.get(0).getFrequency());
        assertEquals("NVDA", result.get(0).getStock().getStockCode());
        assertEquals("英伟达业绩超预期 | 英伟达扩大AI产能", result.get(0).getStock().getTags());

        verify(usStockRssMapper).selectTopStocksByFrequency(anyString(), anyString(), anyInt(), anyInt(), anyList());
        verify(usStockRssMapper).selectFilteredByStockCodes(anyList(), anyString(), anyString(), anyList());
    }

    @Test
    void getATopNStocksWithFrequency_UsesSqlAggregationAndEnrichesTitles() {
        AStockRssAggregate aggregate = new AStockRssAggregate();
        aggregate.setStockCode("000001");
        aggregate.setStockName("平安银行");
        aggregate.setTitle("关于重大合同的公告");
        aggregate.setFrequency(6);

        AStockRss first = new AStockRss();
        first.setStockCode("000001");
        first.setTitle("关于重大合同的公告");

        AStockRss second = new AStockRss();
        second.setStockCode("000001");
        second.setTitle("关于业绩预增的公告");

        when(aStockRssMapper.selectTopStocksByFrequency(anyString(), anyString(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of(aggregate));
        when(aStockRssMapper.selectFilteredByStockCodes(anyList(), anyString(), anyString(), anyList()))
                .thenReturn(List.of(first, second, first));

        List<StockAlertDTO<AStockRss>> result = stockRankService.getATopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 13, 8, 0),
                LocalDateTime.of(2026, 3, 13, 20, 0)
        );

        assertEquals(1, result.size());
        assertEquals(6, result.get(0).getFrequency());
        assertEquals("000001", result.get(0).getStock().getStockCode());
        assertEquals("关于重大合同的公告 | 关于业绩预增的公告", result.get(0).getStock().getTag());

        verify(aStockRssMapper).selectTopStocksByFrequency(anyString(), anyString(), anyInt(), anyInt(), anyList());
        verify(aStockRssMapper).selectFilteredByStockCodes(anyList(), anyString(), anyString(), anyList());
    }
}
