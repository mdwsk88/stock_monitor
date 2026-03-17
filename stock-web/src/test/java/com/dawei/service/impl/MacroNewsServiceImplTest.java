package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroNewsRawMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroNewsServiceImplTest {

    private static final String GOV_POLICY_URL = "https://www.gov.cn/zhengce/";
    private static final String PBC_OMO_URL = "https://www.pbc.gov.cn/zhengcehuobisi/125207/125213/125431/125475/index.html";
    private static final String STATS_RSS_URL = "https://www.stats.gov.cn/sj/zxfb/rss.xml";
    private static final String CSRC_URL =
            "https://www.csrc.gov.cn/searchList/a1a078ee0bc54721ab6b148884c784a8?_isAgg=true&_isJson=true&_template=index&_rangeTimeGte=&_channelName=&page=1&_pageSize=5";
    private static final String EASTMONEY_FAST_NEWS_URL =
            "https://np-weblist.eastmoney.com/comm/web/getFastNewsList?client=web&biz=web_724&fastColumn=102&sortEnd=&pageSize=5&req_trace=macro_shadow&callback=callback";

    @Mock
    private RestTemplate restTemplate;
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

    private MacroNewsServiceImpl macroNewsService;

    @BeforeEach
    void setUp() {
        StockFilterConfig config = new StockFilterConfig();
        config.setMacroFetchLimitPerSource(5);
        MacroNewsSignalService signalService = new MacroNewsSignalService(config);
        MacroThemeRelationService relationService = new MacroThemeRelationService(aStockRssMapper, themeWatchlistMapper, themeAutoPoolService);
        macroNewsService = new MacroNewsServiceImpl(
                restTemplate,
                macroNewsRawMapper,
                macroThemeEventMapper,
                macroThemeStockRelMapper,
                relationService,
                signalService,
                themeAutoPoolService
        );
        lenient().when(aStockRssMapper.selectMaps(any())).thenReturn(List.of());
        lenient().when(themeAutoPoolService.listEnabled()).thenReturn(List.of());
    }

    @Test
    void testFetchAndSaveMacroNews() throws Exception {
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroNewsRawMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeEventMapper.insertIgnore(any())).thenReturn(1);

        mockExchange(GOV_POLICY_URL, """
                <html><body><div class="item03"><div class="list"><ul>
                <li><a href="./202603/content_7062681.htm">国务院办公厅关于加强基层消防工作的意见</a><span>2026-03-14</span></li>
                </ul></div></div></body></html>
                """);
        mockExchange(PBC_OMO_URL, """
                <html><body><table><tr><td>
                <font><a href="/zhengcehuobisi/125207/125213/125431/125475/2026031308534094429/index.html" istitle="true">公开市场业务交易公告 [2026]第48号</a></font>
                <span class="hui12">2026-03-13</span>
                </td></tr></table></body></html>
                """);
        mockExchange(STATS_RSS_URL, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title><![CDATA[2026年2月份居民消费价格同比上涨1.3%]]></title>
                  <channel>数据发布</channel>
                  <link>https://www.stats.gov.cn/sj/zxfb/202603/t20260309_1962732.html</link>
                  <pubDate>2026-03-09 09:30:02</pubDate>
                  <description><![CDATA[国家统计局发布2月份CPI数据。]]></description>
                  <docId>1962732</docId>
                </item></channel></rss>
                """);
        mockExchange(CSRC_URL, """
                {
                  "data": {
                    "results": [
                      {
                        "manuscriptId": "7618628",
                        "title": "证监会发布《关于短线交易监管的若干规定》",
                        "content": "证监会发布短线交易监管规定。",
                        "url": "//www.csrc.gov.cn/csrc/c100028/c7618628/content.shtml",
                        "publishedTime": 1772770703000,
                        "channelName": "证监会要闻"
                      }
                    ]
                  }
                }
                """);
        mockExchange(EASTMONEY_FAST_NEWS_URL, """
                callback({
                  "code": "1",
                  "data": {
                    "fastNewsList": [
                      {
                        "code": "202603153672511254",
                        "showTime": "2026-03-15 09:24:27",
                        "title": "工信部：支持算力基础设施建设并推动人工智能产业发展",
                        "summary": "工信部表示将加大支持力度，推动算力和人工智能产业发展。",
                        "stockList": ["90.BK0428"]
                      }
                    ]
                  }
                })
                """);

        MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();

        assertEquals(5, summary.getScanned());
        assertEquals(5, summary.getSavedRaw());
        assertEquals(5, summary.getSavedEvents());
        assertEquals(0, summary.getFiltered());
        verify(macroNewsRawMapper, times(5)).insertIgnore(any());
        verify(macroThemeEventMapper, times(5)).insertIgnore(any());
    }

    @Test
    void testGetShadowThemeEvents() {
        MacroThemeEvent first = baseEvent("1", "cluster-1", "国家政策", 112, LocalDateTime.of(2026, 3, 16, 9, 0));
        MacroThemeEvent second = baseEvent("2", "cluster-1", "国家政策", 108, LocalDateTime.of(2026, 3, 16, 8, 0));
        MacroThemeEvent third = baseEvent("3", "cluster-2", "宏观数据", 88, LocalDateTime.of(2026, 3, 16, 7, 30));

        when(macroThemeEventMapper.selectList(any())).thenReturn(List.of(first, second, third));
        when(macroThemeStockRelMapper.selectList(any())).thenReturn(List.of());

        List<MacroThemeEvent> result = macroNewsService.getShadowThemeEvents(
                LocalDateTime.of(2026, 3, 15, 0, 0),
                LocalDateTime.of(2026, 3, 16, 23, 0),
                10
        );

        assertEquals(2, result.size());
        assertEquals("国家政策", result.get(0).getThemeName());
        assertEquals(2, result.get(0).getClusterEventCount());
        assertEquals(112, result.get(0).getSignalScore());
    }

    @Test
    void testFetchAndSaveMacroNewsContinuesWhenOneSourceFails() throws Exception {
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroNewsRawMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeEventMapper.insertIgnore(any())).thenReturn(1);

        doThrow(new RuntimeException("SSL failure"))
                .when(restTemplate).exchange(eq(GOV_POLICY_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        mockExchange(PBC_OMO_URL, """
                <html><body><table><tr><td>
                <font><a href="/zhengcehuobisi/125207/125213/125431/125475/2026031308534094429/index.html" istitle="true">公开市场业务交易公告 [2026]第48号</a></font>
                <span class="hui12">2026-03-13</span>
                </td></tr></table></body></html>
                """);
        mockExchange(STATS_RSS_URL, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title><![CDATA[2026年2月份居民消费价格同比上涨1.3%]]></title>
                  <channel>数据发布</channel>
                  <link>https://www.stats.gov.cn/sj/zxfb/202603/t20260309_1962732.html</link>
                  <pubDate>2026-03-09 09:30:02</pubDate>
                  <description><![CDATA[国家统计局发布2月份CPI数据。]]></description>
                  <docId>1962732</docId>
                </item></channel></rss>
                """);
        mockExchange(CSRC_URL, """
                {
                  "data": {
                    "results": [
                      {
                        "manuscriptId": "7618628",
                        "title": "证监会发布《关于短线交易监管的若干规定》",
                        "content": "证监会发布短线交易监管规定。",
                        "url": "//www.csrc.gov.cn/csrc/c100028/c7618628/content.shtml",
                        "publishedTime": 1772770703000,
                        "channelName": "证监会要闻"
                      }
                    ]
                  }
                }
                """);
        mockExchange(EASTMONEY_FAST_NEWS_URL, """
                callback({
                  "code": "1",
                  "data": {
                    "fastNewsList": [
                      {
                        "code": "202603153672511254",
                        "showTime": "2026-03-15 09:24:27",
                        "title": "工信部：支持算力基础设施建设并推动人工智能产业发展",
                        "summary": "工信部表示将加大支持力度，推动算力和人工智能产业发展。",
                        "stockList": ["90.BK0428"]
                      }
                    ]
                  }
                })
                """);

        MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();

        assertEquals(4, summary.getScanned());
        assertEquals(4, summary.getSavedRaw());
        assertEquals(4, summary.getSavedEvents());
        assertEquals(0, summary.getFiltered());
        verify(macroNewsRawMapper, times(4)).insertIgnore(any());
        verify(macroThemeEventMapper, times(4)).insertIgnore(any());
    }

    @Test
    void testFetchAndSaveMacroNewsBuildsRelationsFromWatchlist() throws Exception {
        ThemeWatchlist mapping = new ThemeWatchlist();
        mapping.setThemeName("算力");
        mapping.setStockCode("000977");
        mapping.setStockName("浪潮信息");
        mapping.setPriority(3);
        mapping.setEnabled(1);
        mapping.setReason("核心观察池");
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of(mapping));
        when(macroNewsRawMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeEventMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);

        mockExchange(GOV_POLICY_URL, """
                <html><body><div class="item03"><div class="list"><ul>
                <li><a href="./202603/content_7062681.htm">国务院办公厅关于加强基层消防工作的意见</a><span>2026-03-14</span></li>
                </ul></div></div></body></html>
                """);
        mockExchange(PBC_OMO_URL, """
                <html><body><table><tr><td>
                <font><a href="/zhengcehuobisi/125207/125213/125431/125475/2026031308534094429/index.html" istitle="true">公开市场业务交易公告 [2026]第48号</a></font>
                <span class="hui12">2026-03-13</span>
                </td></tr></table></body></html>
                """);
        mockExchange(STATS_RSS_URL, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title><![CDATA[2026年2月份居民消费价格同比上涨1.3%]]></title>
                  <channel>数据发布</channel>
                  <link>https://www.stats.gov.cn/sj/zxfb/202603/t20260309_1962732.html</link>
                  <pubDate>2026-03-09 09:30:02</pubDate>
                  <description><![CDATA[国家统计局发布2月份CPI数据。]]></description>
                  <docId>1962732</docId>
                </item></channel></rss>
                """);
        mockExchange(CSRC_URL, """
                {
                  "data": {
                    "results": [
                      {
                        "manuscriptId": "7618628",
                        "title": "证监会发布《关于短线交易监管的若干规定》",
                        "content": "证监会发布短线交易监管规定。",
                        "url": "//www.csrc.gov.cn/csrc/c100028/c7618628/content.shtml",
                        "publishedTime": 1772770703000,
                        "channelName": "证监会要闻"
                      }
                    ]
                  }
                }
                """);
        mockExchange(EASTMONEY_FAST_NEWS_URL, """
                callback({
                  "code": "1",
                  "data": {
                    "fastNewsList": [
                      {
                        "code": "202603153672511254",
                        "showTime": "2026-03-15 09:24:27",
                        "title": "工信部：支持算力基础设施建设并推动人工智能产业发展",
                        "summary": "工信部表示将加大支持力度，推动算力和人工智能产业发展。",
                        "stockList": ["90.BK0428"]
                      }
                    ]
                  }
                })
                """);

        MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();

        assertEquals(1, summary.getSavedRelations());
        verify(macroThemeStockRelMapper).insertIgnore(any());
        verify(themeAutoPoolService, never()).recordExplicitHit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testFetchAndSaveMacroNewsRecordsAutoPoolOnExplicitRelation() throws Exception {
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroNewsRawMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeEventMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);
        when(aStockRssMapper.selectMaps(any())).thenReturn(List.of(
                java.util.Map.of("stock_code", "000977", "stock_name", "浪潮信息")
        ));

        mockExchange(GOV_POLICY_URL, """
                <html><body><div class="item03"><div class="list"><ul>
                <li><a href="./202603/content_7062681.htm">国务院办公厅关于加强基层消防工作的意见</a><span>2026-03-14</span></li>
                </ul></div></div></body></html>
                """);
        mockExchange(PBC_OMO_URL, """
                <html><body><table><tr><td>
                <font><a href="/zhengcehuobisi/125207/125213/125431/125475/2026031308534094429/index.html" istitle="true">公开市场业务交易公告 [2026]第48号</a></font>
                <span class="hui12">2026-03-13</span>
                </td></tr></table></body></html>
                """);
        mockExchange(STATS_RSS_URL, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title><![CDATA[2026年2月份居民消费价格同比上涨1.3%]]></title>
                  <channel>数据发布</channel>
                  <link>https://www.stats.gov.cn/sj/zxfb/202603/t20260309_1962732.html</link>
                  <pubDate>2026-03-09 09:30:02</pubDate>
                  <description><![CDATA[国家统计局发布2月份CPI数据。]]></description>
                  <docId>1962732</docId>
                </item></channel></rss>
                """);
        mockExchange(CSRC_URL, """
                {
                  "data": {
                    "results": [
                      {
                        "manuscriptId": "7618628",
                        "title": "证监会发布《关于短线交易监管的若干规定》",
                        "content": "证监会发布短线交易监管规定。",
                        "url": "//www.csrc.gov.cn/csrc/c100028/c7618628/content.shtml",
                        "publishedTime": 1772770703000,
                        "channelName": "证监会要闻"
                      }
                    ]
                  }
                }
                """);
        mockExchange(EASTMONEY_FAST_NEWS_URL, """
                callback({
                  "code": "1",
                  "data": {
                    "fastNewsList": [
                      {
                        "code": "202603153672511254",
                        "showTime": "2026-03-15 09:24:27",
                        "title": "工信部：支持算力基础设施建设，浪潮信息等厂商受关注",
                        "summary": "工信部表示将加大支持力度，推动算力产业发展，浪潮信息等厂商受关注。",
                        "stockList": ["90.BK0428"]
                      }
                    ]
                  }
                })
                """);

        MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();

        assertEquals(1, summary.getSavedRelations());
        verify(themeAutoPoolService).recordExplicitHit(eq("算力"), eq("000977"), eq("浪潮信息"), any(), any(), any());
    }

    @Test
    void testFetchAndSaveMacroNewsLearnsAutoPoolAndAppliesToLaterItems() throws Exception {
        when(themeWatchlistMapper.selectList(any())).thenReturn(List.of());
        when(macroNewsRawMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeEventMapper.insertIgnore(any())).thenReturn(1);
        when(macroThemeStockRelMapper.insertIgnore(any())).thenReturn(1);
        when(aStockRssMapper.selectMaps(any())).thenReturn(List.of(
                java.util.Map.of("stock_code", "000099", "stock_name", "中信海直")
        ));
        ThemeAutoPoolCandidate learnedCandidate = new ThemeAutoPoolCandidate();
        learnedCandidate.setId("auto-1");
        learnedCandidate.setThemeName("低空经济");
        learnedCandidate.setStockCode("000099");
        learnedCandidate.setStockName("中信海直");
        learnedCandidate.setCandidateScore(85);
        learnedCandidate.setEnabled(1);
        when(themeAutoPoolService.recordExplicitHit(any(), any(), any(), any(), any(), any()))
                .thenReturn(learnedCandidate);

        mockExchange(GOV_POLICY_URL, """
                <html><body><div class="item03"><div class="list"><ul>
                <li><a href="./202603/content_7062681.htm">国务院办公厅关于促进低空经济高质量发展的意见 中信海直等运营环节受关注</a><span>2026-03-14</span></li>
                </ul></div></div></body></html>
                """);
        mockExchange(PBC_OMO_URL, """
                <html><body><table><tr><td>
                <font><a href="/zhengcehuobisi/125207/125213/125431/125475/2026031308534094429/index.html" istitle="true">公开市场业务交易公告 [2026]第48号</a></font>
                <span class="hui12">2026-03-13</span>
                </td></tr></table></body></html>
                """);
        mockExchange(STATS_RSS_URL, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title><![CDATA[2026年2月份居民消费价格同比上涨1.3%]]></title>
                  <channel>数据发布</channel>
                  <link>https://www.stats.gov.cn/sj/zxfb/202603/t20260309_1962732.html</link>
                  <pubDate>2026-03-09 09:30:02</pubDate>
                  <description><![CDATA[国家统计局发布2月份CPI数据。]]></description>
                  <docId>1962732</docId>
                </item></channel></rss>
                """);
        mockExchange(CSRC_URL, """
                {
                  "data": {
                    "results": [
                      {
                        "manuscriptId": "7618628",
                        "title": "证监会发布《关于短线交易监管的若干规定》",
                        "content": "证监会发布短线交易监管规定。",
                        "url": "//www.csrc.gov.cn/csrc/c100028/c7618628/content.shtml",
                        "publishedTime": 1772770703000,
                        "channelName": "证监会要闻"
                      }
                    ]
                  }
                }
                """);
        mockExchange(EASTMONEY_FAST_NEWS_URL, """
                callback({
                  "code": "1",
                  "data": {
                    "fastNewsList": [
                      {
                        "code": "202603153672511254",
                        "showTime": "2026-03-15 09:24:27",
                        "title": "工信部：支持低空经济基础设施建设",
                        "summary": "工信部表示将加大支持力度，推动低空经济产业发展。",
                        "stockList": ["90.BK0428"]
                      }
                    ]
                  }
                })
                """);

        MacroNewsService.FetchSummary summary = macroNewsService.fetchAndSaveMacroNews();

        assertTrue(summary.getSavedRelations() >= 2);
        verify(themeAutoPoolService).recordExplicitHit(eq("低空经济"), eq("000099"), eq("中信海直"), any(), any(), any());
        verify(macroThemeStockRelMapper, times(2)).insertIgnore(any());
    }

    private void mockExchange(String url, String body) {
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private MacroThemeEvent baseEvent(String id, String clusterKey, String theme, int score, LocalDateTime pubDate) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId(id);
        event.setClusterKey(clusterKey);
        event.setThemeName(theme);
        event.setSignalScore(score);
        event.setSignalSide("中性");
        event.setTitle(theme);
        event.setPubDate(pubDate);
        return event;
    }
}
