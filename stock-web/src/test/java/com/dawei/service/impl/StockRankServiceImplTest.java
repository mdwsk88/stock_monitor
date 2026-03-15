package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
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
import static org.mockito.ArgumentMatchers.any;
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
    private AStockSignalService aStockSignalService;

    @InjectMocks
    private StockRankServiceImpl stockRankService;

    @BeforeEach
    void setUp() {
        filterConfig = new StockFilterConfig();
        filterConfig.setFrequencyThreshold(3);
        filterConfig.setARankingSignalThreshold(60);
        aStockSignalService = new AStockSignalService(filterConfig);
        stockRankService = new StockRankServiceImpl(usStockRssMapper, aStockRssMapper, filterConfig, aStockSignalService);
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
        AStockRss first = new AStockRss();
        first.setStockCode("000001");
        first.setStockName("平安银行");
        first.setTitle("平安银行:关于中标10亿元算力项目的公告");
        first.setTag("重大合同");
        first.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 0));

        AStockRss second = new AStockRss();
        second.setStockCode("000001");
        second.setStockName("平安银行");
        second.setTitle("平安银行:关于中标10亿元算力项目的提示性公告");
        second.setTag("重大合同");
        second.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 5));

        AStockRss noise = new AStockRss();
        noise.setStockCode("000001");
        noise.setStockName("平安银行");
        noise.setTitle("平安银行:关于召开董事会会议通知");
        noise.setTag("董事会决议");
        noise.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 8));

        AStockRss negative = new AStockRss();
        negative.setStockCode("000002");
        negative.setStockName("万科A");
        negative.setTitle("万科A:关于收到立案告知书的公告");
        negative.setTag("其他");
        negative.setPubDate(LocalDateTime.of(2026, 3, 13, 11, 0));

        when(aStockRssMapper.selectList(any())).thenReturn(List.of(first, second, noise, negative));

        List<StockAlertDTO<AStockRss>> result = stockRankService.getATopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 13, 8, 0),
                LocalDateTime.of(2026, 3, 13, 20, 0)
        );

        assertEquals(2, result.size());
        StockAlertDTO<AStockRss> bullish = result.stream()
                .filter(dto -> "000001".equals(dto.getStock().getStockCode()))
                .findFirst()
                .orElseThrow();
        StockAlertDTO<AStockRss> bearish = result.stream()
                .filter(dto -> "000002".equals(dto.getStock().getStockCode()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, bullish.getFrequency());
        assertEquals(1, bullish.getEventCount());
        assertEquals("利多", bullish.getSignalSide());
        assertTrue(bullish.getStock().getRelatedTitles().contains("中标10亿元算力项目"));
        assertTrue(bullish.getStock().getClusterHighlights().contains("重大合同"));

        assertEquals("利空", bearish.getSignalSide());
        assertTrue(bearish.getSignalScore() >= 60);
        assertTrue(bearish.getStock().getAnalysisHint().contains("最高优先级事件"));

        verify(aStockRssMapper).selectList(any());
    }

    @Test
    void getATopNStocksWithFrequency_SortsBySignalScoreDescending() {
        AStockRss high1 = new AStockRss();
        high1.setStockCode("601669");
        high1.setStockName("中国电建");
        high1.setTitle("中国电建:关于签署重大合同的公告");
        high1.setTag("重大合同");
        high1.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 0));

        AStockRss high2 = new AStockRss();
        high2.setStockCode("601669");
        high2.setStockName("中国电建");
        high2.setTitle("中国电建:关于收到中标通知书的公告");
        high2.setTag("重大合同");
        high2.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 20));

        AStockRss low = new AStockRss();
        low.setStockCode("000002");
        low.setStockName("万科A");
        low.setTitle("万科A:关于收到立案告知书的公告");
        low.setTag("其他");
        low.setPubDate(LocalDateTime.of(2026, 3, 13, 11, 0));

        when(aStockRssMapper.selectList(any())).thenReturn(List.of(high1, high2, low));

        List<StockAlertDTO<AStockRss>> result = stockRankService.getATopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 13, 8, 0),
                LocalDateTime.of(2026, 3, 13, 20, 0)
        );

        assertEquals(2, result.size());
        assertEquals("601669", result.get(0).getStock().getStockCode());
        assertTrue(result.get(0).getSignalScore() > result.get(1).getSignalScore());
        assertEquals("000002", result.get(1).getStock().getStockCode());
    }

    @Test
    void getATopNStocksWithFrequency_DowngradesPreliminaryRestructuringBundle() {
        AStockRss restructuring1 = new AStockRss();
        restructuring1.setStockCode("601555");
        restructuring1.setStockName("东吴证券");
        restructuring1.setTitle("东吴证券:关于披露发行股份及支付现金购买资产暨关联交易预案的一般风险提示暨公司股票复牌的公告");
        restructuring1.setTag("风险提示性公告");
        restructuring1.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 0));

        AStockRss restructuring2 = new AStockRss();
        restructuring2.setStockCode("601555");
        restructuring2.setStockName("东吴证券");
        restructuring2.setTitle("东吴证券:发行股份及支付现金购买资产暨关联交易预案摘要");
        restructuring2.setTag("其他");
        restructuring2.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 5));

        AStockRss restructuring3 = new AStockRss();
        restructuring3.setStockCode("601555");
        restructuring3.setStockName("东吴证券");
        restructuring3.setTitle("东吴证券:发行股份及支付现金购买资产暨关联交易预案");
        restructuring3.setTag("其他");
        restructuring3.setPubDate(LocalDateTime.of(2026, 3, 13, 10, 10));

        AStockRss contract1 = new AStockRss();
        contract1.setStockCode("601669");
        contract1.setStockName("中国电建");
        contract1.setTitle("中国电建:关于签署重大合同的公告");
        contract1.setTag("重大合同");
        contract1.setPubDate(LocalDateTime.of(2026, 3, 13, 11, 0));

        AStockRss contract2 = new AStockRss();
        contract2.setStockCode("601669");
        contract2.setStockName("中国电建");
        contract2.setTitle("中国电建:关于收到中标通知书的公告");
        contract2.setTag("重大合同");
        contract2.setPubDate(LocalDateTime.of(2026, 3, 13, 11, 10));

        when(aStockRssMapper.selectList(any())).thenReturn(List.of(
                restructuring1, restructuring2, restructuring3, contract1, contract2
        ));

        List<StockAlertDTO<AStockRss>> result = stockRankService.getATopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 13, 8, 0),
                LocalDateTime.of(2026, 3, 13, 20, 0)
        );

        assertEquals(1, result.size());
        assertEquals("601669", result.get(0).getStock().getStockCode());
        assertTrue(result.stream().noneMatch(dto -> "601555".equals(dto.getStock().getStockCode())));
    }

    @Test
    void getATopNStocksWithFrequency_FiltersNonEquityCodesFromHistoricalDirtyRows() {
        AStockRss convertible = new AStockRss();
        convertible.setStockCode("113666");
        convertible.setStockName("爱玛转债");
        convertible.setTitle("爱玛转债:关于中标重大合同的公告");
        convertible.setTag("重大合同");
        convertible.setPubDate(LocalDateTime.of(2026, 3, 15, 10, 0));

        AStockRss equity = new AStockRss();
        equity.setStockCode("603529");
        equity.setStockName("爱玛科技");
        equity.setTitle("爱玛科技:关于签署重大合同的公告");
        equity.setTag("重大合同");
        equity.setPubDate(LocalDateTime.of(2026, 3, 15, 10, 10));

        when(aStockRssMapper.selectList(any())).thenReturn(List.of(convertible, equity));

        List<StockAlertDTO<AStockRss>> result = stockRankService.getATopNStocksWithFrequency(
                5,
                LocalDateTime.of(2026, 3, 15, 8, 0),
                LocalDateTime.of(2026, 3, 15, 20, 0)
        );

        assertEquals(1, result.size());
        assertEquals("603529", result.get(0).getStock().getStockCode());
        assertTrue(result.stream().noneMatch(dto -> "113666".equals(dto.getStock().getStockCode())));
    }
}
