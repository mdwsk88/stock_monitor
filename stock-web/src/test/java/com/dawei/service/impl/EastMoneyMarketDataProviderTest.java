package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MarketSnapshot;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EastMoneyMarketDataProviderTest {

    private static final String INDEX_URL =
            "https://push2.eastmoney.com/api/qt/ulist.np/get"
                    + "?secids=1.000001,0.399001,0.399006"
                    + "&fields=f12,f14,f2,f3"
                    + "&fltt=2&invt=2&ut=fa5fd1943c7b386f172d6893dbfba10b";
    private static final String BREADTH_URL =
            "https://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=6000&po=1&np=1&fltt=2&invt=2&fid=f3"
                    + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
                    + "&fields=f3&ut=fa5fd1943c7b386f172d6893dbfba10b";
    private static final String TENCENT_INDEX_URL =
            "https://qt.gtimg.cn/q=s_sh000001,s_sz399001,s_sz399006";
    private static final String TENCENT_SAMPLE_URL =
            "https://qt.gtimg.cn/q=s_sz300750,s_sh600519,s_sz000001,s_sz002594";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private StockFilterConfig filterConfig;
    private EastMoneyMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        filterConfig = new StockFilterConfig();
        filterConfig.setMarketBreadthFetchLimit(6000);
        filterConfig.setMarketSnapshotRetryAttempts(3);
        filterConfig.setMarketSnapshotRetryBackoffMillis(0L);
        provider = new EastMoneyMarketDataProvider(restTemplate, filterConfig);
    }

    @Test
    void fetchSnapshot_ShouldParseIndicesAndBreadth() {
        server.expect(requestTo(INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"diff":[
                          {"f12":"000001","f3":1.23},
                          {"f12":"399001","f3":2.34},
                          {"f12":"399006","f3":3.45}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BREADTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"diff":[
                          {"f3":10.02},
                          {"f3":3.14},
                          {"f3":0.00},
                          {"f3":-1.25},
                          {"f3":-9.96}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot snapshot = provider.fetchSnapshot();

        assertEquals(1.23d, snapshot.getShChangePct());
        assertEquals(2.34d, snapshot.getSzChangePct());
        assertEquals(3.45d, snapshot.getCybChangePct());
        assertEquals(2, snapshot.getUpCount());
        assertEquals(2, snapshot.getDownCount());
        assertEquals(1, snapshot.getFlatCount());
        assertEquals(1, snapshot.getLimitUpCount());
        assertEquals(1, snapshot.getLimitDownCount());
        server.verify();
    }

    @Test
    void fetchSnapshot_ShouldRetryWhenIndexEndpointFailsTransiently() {
        server.expect(requestTo(INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Unexpected end of file from server");
                });
        server.expect(requestTo(INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"diff":[
                          {"f12":"000001","f3":-1.11},
                          {"f12":"399001","f3":-2.22},
                          {"f12":"399006","f3":-3.33}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BREADTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"diff":[
                          {"f3":1.02},
                          {"f3":-0.14}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot snapshot = provider.fetchSnapshot();

        assertEquals(-1.11d, snapshot.getShChangePct());
        assertEquals(-2.22d, snapshot.getSzChangePct());
        assertEquals(-3.33d, snapshot.getCybChangePct());
        server.verify();
    }

    @Test
    void fetchSnapshot_ShouldFallbackToTencentIndexQuotesWhenEastMoneyFails() {
        filterConfig.setMarketSnapshotRetryAttempts(1);
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        provider = new EastMoneyMarketDataProvider(restTemplate, filterConfig);

        server.expect(requestTo(INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Unexpected end of file from server");
                });
        server.expect(requestTo(TENCENT_INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        v_s_sh000001="1~上证指数~000001~3813.28~-143.77~-3.63~804738850~108624829~~626309.80~ZS~";
                        v_s_sz399001="51~深证成指~399001~13345.51~-520.69~-3.76~852353152~134529902~~428364.98~ZS~";
                        v_s_sz399006="51~创业板指~399006~3235.22~-116.88~-3.49~239669171~61241648~~173916.03~ZS~";
                        """, MediaType.TEXT_PLAIN));
        server.expect(requestTo(BREADTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Empty reply from server");
                });

        MarketSnapshot snapshot = provider.fetchSnapshot();

        assertEquals(-3.63d, snapshot.getShChangePct());
        assertEquals(-3.76d, snapshot.getSzChangePct());
        assertEquals(-3.49d, snapshot.getCybChangePct());
        assertEquals("TENCENT_QUOTE+NO_BREADTH", snapshot.getSource());
        assertEquals(0, snapshot.getUpCount());
        server.verify();
    }

    @Test
    void fetchSnapshot_ShouldFallbackToTencentSampleBreadthWhenEastMoneyBreadthFails() {
        AStockRssMapper rssMapper = Mockito.mock(AStockRssMapper.class);
        ThemeWatchlistMapper watchlistMapper = Mockito.mock(ThemeWatchlistMapper.class);
        ThemeAutoPoolCandidateMapper autoPoolMapper = Mockito.mock(ThemeAutoPoolCandidateMapper.class);
        filterConfig.setMarketSnapshotRetryAttempts(1);
        filterConfig.setMarketBreadthSampleSize(4);
        filterConfig.setMarketQuoteBatchSize(10);
        provider = new EastMoneyMarketDataProvider(restTemplate, filterConfig, rssMapper, watchlistMapper, autoPoolMapper);

        when(watchlistMapper.selectList(any())).thenReturn(List.of());
        when(autoPoolMapper.selectList(any())).thenReturn(List.of());
        when(rssMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("stockCode", "300750"),
                Map.of("stockCode", "600519"),
                Map.of("stockCode", "000001"),
                Map.of("stockCode", "002594")
        ));

        server.expect(requestTo(INDEX_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"diff":[
                          {"f12":"000001","f3":1.23},
                          {"f12":"399001","f3":2.34},
                          {"f12":"399006","f3":3.45}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BREADTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Empty reply from server");
                });
        server.expect(requestTo(TENCENT_SAMPLE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        v_s_sz300750="51~宁德时代~300750~401.99~-11.01~-2.67~446141~1819334~~18346.30~GP-A-CYB~";
                        v_s_sh600519="1~贵州茅台~600519~1408.07~-36.93~-2.56~47256~666971~~17632.84~GP-A~";
                        v_s_sz000001="51~平安银行~000001~10.45~-0.32~-2.97~1580088~166633~~2027.92~GP-A~";
                        v_s_sz002594="51~比亚迪~002594~107.63~4.60~4.46~1782446~1928492~~9812.84~GP-A~";
                        """, MediaType.TEXT_PLAIN));

        MarketSnapshot snapshot = provider.fetchSnapshot();

        assertEquals(1, snapshot.getUpCount());
        assertEquals(3, snapshot.getDownCount());
        assertEquals(0, snapshot.getFlatCount());
        assertEquals(4, snapshot.getBreadthSampleSize());
        assertEquals("EASTMONEY+SAMPLE_BREADTH", snapshot.getSource());
        server.verify();
    }
}
