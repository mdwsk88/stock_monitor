package com.dawei.service.impl;

import com.dawei.entity.*;
import com.dawei.enums.StockTag;
import com.dawei.service.RssService;
import com.dawei.service.StockService;
import com.dawei.common.utils.GMTDateConverter;
import com.dawei.utils.*;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RssServiceImpl implements RssService {
    @Value("${stock.push.us-enabled:false}")
    private boolean usPushEnabled;

    @Resource
    private StockService stockService;

    @Resource
    private DingTalkApi dingTalkApi;

    @Resource
    private WeComApi weComApi;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AStockSignalService aStockSignalService;

    public static final String RSS_URL = "https://www.stocktitan.net/rss";
    public static final String A_STOCK_NOTICE_URL = "https://np-anotice-stock.eastmoney.com/api/security/ann";
    private static final String A_STOCK_NOTICE_QUERY = "?sr=-1&page_size=100&ann_type=SHA,CYB,SZA,BJA,INV&client_source=web&f_node=3,5,6&s_node=0";
    private static final DateTimeFormatter A_STOCK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    @Override
    public void displayRss() throws Exception {


        List<SyndEntry> syndEntryList = this.fetchRssReed(RSS_URL);

        List<USStockMsg> stockMsgList = new ArrayList<>();

        //        System.out.println(syndEntryList);

        if (syndEntryList == null || syndEntryList.isEmpty()) {
            System.out.println("RSS 列表为空");
            return;
        }

        for (SyndEntry entry : syndEntryList) {
            try {
                USStockRss stockNews = new USStockRss();

                // 获得股票异动信息的标题
                String title = entry.getTitle();
                String titleEn = getStockTitle(title);
                stockNews.setTitle(titleEn);

                // 获得股票异动信息的链接地址
                stockNews.setLink(entry.getLink());

                // 获得股票异动信息的发布时间（GMT时间和北京时间）
                Date gmtDateTemp = entry.getPublishedDate();
                LocalDateTime gmtDate = GMTDateConverter.convertGmt(gmtDateTemp);
                stockNews.setPubDateGmt(gmtDate);
                stockNews.setPubDateBj(GMTDateConverter.convertGmtToBeijing(gmtDateTemp));

                // 获得股票异动信息的股票代码
                String stockCode = getStockCode(title);
                stockNews.setStockCode(stockCode);

                // 【优化】不再调用百度翻译 API，直接存储英文标题
                // 后续使用大模型进行翻译和总结，降低成本
                stockNews.setTitleZh(titleEn);

                // 获得股票异动信息的标签
                try {
                    List<String> tagsList = StockTitanCrawler.getTags(titleEn);
                    stockNews.setTags(getTagsZh(tagsList));
                } catch (Exception e) {
                    // 此处因为频繁访问页面抓取数据，可能导致429的反爬虫异常，无需处理，tags无所谓，可以直接设为空值
                    log.warn("获取标签失败，股票代码: {}, 原因: {}", stockCode, e.getMessage());
                    stockNews.setTags("");
                }

                log.info("美股新闻: {}", stockNews);
                if (!stockService.saveStockNewsIfAbsent(stockNews)) {
                    log.info("股票代码为【{}】的已存在，跳过。。。", stockCode);
                    continue;
                }

                USStockMsg stockMsg = new USStockMsg();
                BeanUtils.copyProperties(stockNews, stockMsg);

                stockMsg.setPubDateBj(GMTDateConverter.getBeijingTime(gmtDateTemp));

                Long counts24Hour = stockService.getStockUnusualCounts(stockNews,
                        GMTDateConverter.minus24Hour(gmtDate),
                        GMTDateConverter.plus1Minute(gmtDate));
                Long counts3Day = stockService.getStockUnusualCounts(stockNews,
                        GMTDateConverter.minus3Day(gmtDate),
                        GMTDateConverter.plus1Minute(gmtDate));
                Long counts1Week = stockService.getStockUnusualCounts(stockNews,
                        GMTDateConverter.minus1Week(gmtDate),
                        GMTDateConverter.plus1Minute(gmtDate));
                stockMsg.setCounts24Hour(counts24Hour.intValue());
                stockMsg.setCounts3Day(counts3Day.intValue());
                stockMsg.setCounts1Week(counts1Week.intValue());

                log.info("美股消息: {}", stockMsg);
                stockMsgList.add(stockMsg);
            } catch (Exception e) {
                log.error("处理美股单条新闻失败，标题: {}, 原因: {}", entry.getTitle(), e.getMessage(), e);
                // continue 保证不影响下一条新闻的处理
            }
        }

        if (!stockMsgList.isEmpty() && usPushEnabled) {
            //dingTalkApi.sendTextMessage(dingTalkApi.formatStockInfoFromList(stockMsgList));
            weComApi.sendMarkdownMessageAsync(weComApi.formatStockInfoFromList(stockMsgList), WeComApi.MarketType.US);
            //weComApi.sendTextMessage(weComApi.formatStockInfoTextFromList(stockMsgList), WeComApi.MarketType.US);
        } else if (!stockMsgList.isEmpty()) {
            log.info("美股实时推送已关闭，跳过发送 {} 条美股异动消息", stockMsgList.size());
        }

    }

    @Override
    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception {
        URL url = new URL(rssUrl);
        SyndFeedInput input =  new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));
        return feed.getEntries();
    }

        /**
         * @Description: 处理股票标题
         * @Author dawei
         * @param title
         * @return String
         */
        private String getStockTitle(String title) {
            String[] titleArr = title.split("\\|");
            return titleArr[0].trim();
        }


    /**
     * @Description: 处理股票代码
     * @Author dawei
     * @param title
     * @return String
     */
    private String getStockCode(String title) {
        String[] titleArr = title.split("\\|");
        String stockStr = titleArr[titleArr.length - 1];

        String[] stockCodeArr = stockStr.split("Stock News");

        return stockCodeArr[0].trim();
    }


    /**
     * @Description: 从股票标签枚举中获取映射的中文标签（带有emoji）
     * @Author dawei
     * @param list
     * @return String
     */
    private String getTagsZh(List<String> list) {

        String tagStr = "";

        for (int i = 0; i < list.size(); i++) {
            String tag = list.get(i);
            tagStr += StockTag.getTagValue(tag);

            if (i < list.size() - 1) {
                tagStr += ", ";
            }
        }

        return tagStr;
    }

    // ============== A股相关方法 ==============

    @Override
    public void fetchAndSaveAStockNotices() throws Exception {
        List<AStockMsg> aStockMsgList = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ObjectMapper mapper = new ObjectMapper();

        for (int pageIndex = 1; pageIndex <= aStockSignalService.getFetchPageCount(); pageIndex++) {
            String requestUrl = buildAStockNoticeUrl(pageIndex);
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            JsonNode root = mapper.readTree(json);
            JsonNode list = root.path("data").path("list");

            if (!list.isArray() || list.isEmpty()) {
                log.info("A股公告第 [{}] 页为空，结束回扫", pageIndex);
                break;
            }

            for (JsonNode node : list) {
                try {
                    AStockRss aStockRss = buildAStockNotice(node);
                    if (aStockRss == null) {
                        continue;
                    }
                    if (!aStockSignalService.enrichNotice(aStockRss)) {
                        log.debug("A股公告命中硬过滤，跳过：【{} - {}】{}", aStockRss.getStockCode(), aStockRss.getStockName(), aStockRss.getTitle());
                        continue;
                    }
                    if (!stockService.saveAStockNewsIfAbsent(aStockRss)) {
                        log.info("A股公告已存在，跳过：【{} - {}】{}", aStockRss.getStockCode(), aStockRss.getStockName(), aStockRss.getTitle());
                        continue;
                    }

                    log.info("A股公告保存成功：{}", aStockRss);

                    if (!aStockSignalService.isRealtimeAlert(aStockRss)) {
                        continue;
                    }

                    Long counts24Hour = stockService.getAStockNoticeCounts(aStockRss.getStockCode(),
                            GMTDateConverter.minus24Hour(aStockRss.getPubDate()),
                            GMTDateConverter.plus1Minute(aStockRss.getPubDate()));
                    Long counts3Day = stockService.getAStockNoticeCounts(aStockRss.getStockCode(),
                            GMTDateConverter.minus3Day(aStockRss.getPubDate()),
                            GMTDateConverter.plus1Minute(aStockRss.getPubDate()));
                    Long counts1Week = stockService.getAStockNoticeCounts(aStockRss.getStockCode(),
                            GMTDateConverter.minus1Week(aStockRss.getPubDate()),
                            GMTDateConverter.plus1Minute(aStockRss.getPubDate()));

                    AStockMsg aStockMsg = new AStockMsg();
                    aStockMsg.setStockCode(aStockRss.getStockCode());
                    aStockMsg.setStockName(aStockRss.getStockName());
                    aStockMsg.setTitle(aStockRss.getTitle());
                    aStockMsg.setTag(aStockRss.getTag());
                    aStockMsg.setPubDate(aStockRss.getPubDate().format(A_STOCK_TIME_FORMATTER));
                    aStockMsg.setEventType(aStockRss.getEventType());
                    aStockMsg.setSignalSide(aStockRss.getSignalSide());
                    aStockMsg.setSignalScore(aStockRss.getSignalScore());
                    aStockMsg.setCounts24Hour(counts24Hour.intValue());
                    aStockMsg.setCounts3Day(counts3Day.intValue());
                    aStockMsg.setCounts1Week(counts1Week.intValue());
                    aStockMsgList.add(aStockMsg);
                } catch (Exception e) {
                    log.error("【警告】解析单条A股公告失败，跳过。原因: {}", e.getMessage());
                }
            }

            if (list.size() < 100) {
                break;
            }
        }

        if (!aStockMsgList.isEmpty()) {
            weComApi.sendMarkdownMessageAsync(weComApi.formatAStockInfoFromList(aStockMsgList), WeComApi.MarketType.A);
        }
    }

    private String buildAStockNoticeUrl(int pageIndex) {
        return A_STOCK_NOTICE_URL + A_STOCK_NOTICE_QUERY + "&page_index=" + pageIndex;
    }

    private AStockRss buildAStockNotice(JsonNode node) {
        SecurityRef securityRef = resolvePrimarySecurity(node.path("codes"));
        if (securityRef == null) {
            return null;
        }

        String title = node.path("title").asText("");
        if (title.isBlank()) {
            return null;
        }

        LocalDateTime pubDate = parseDisplayTime(node.path("display_time").asText(""));
        String artCode = node.path("art_code").asText("");
        String stockCode = securityRef.stockCode();

        AStockRss aStockRss = new AStockRss();
        aStockRss.setId(UUID.randomUUID().toString().replace("-", ""));
        aStockRss.setArtCode(artCode);
        aStockRss.setStockCode(stockCode);
        aStockRss.setStockName(securityRef.stockName());
        aStockRss.setTitle(title);
        aStockRss.setTag(extractTagText(node.path("columns")));
        aStockRss.setPubDate(pubDate);
        aStockRss.setCreateTime(LocalDateTime.now());
        aStockRss.setLink("https://data.eastmoney.com/notices/detail/" + stockCode + "/" + artCode + ".html");
        return aStockRss;
    }

    private SecurityRef resolvePrimarySecurity(JsonNode codesNode) {
        if (!codesNode.isArray() || codesNode.isEmpty()) {
            return null;
        }

        SecurityRef fallback = null;
        for (JsonNode codeNode : codesNode) {
            String stockCode = codeNode.path("stock_code").asText("");
            String stockName = codeNode.path("short_name").asText("");
            if (stockCode.isBlank()) {
                continue;
            }
            SecurityRef candidate = new SecurityRef(stockCode, stockName);
            if (fallback == null) {
                fallback = candidate;
            }
            if (aStockSignalService.isPreferredEquityCode(stockCode)) {
                return candidate;
            }
        }
        return fallback;
    }

    private String extractTagText(JsonNode columnsNode) {
        List<String> tags = new ArrayList<>();
        if (columnsNode != null && columnsNode.isArray()) {
            for (JsonNode columnNode : columnsNode) {
                String tag = columnNode.path("column_name").asText("");
                if (!tag.isBlank()) {
                    tags.add(tag);
                }
            }
        }
        return aStockSignalService.normalizeTagText(tags);
    }

    private LocalDateTime parseDisplayTime(String displayTime) {
        String value = displayTime;
        if (value != null && value.length() > 19) {
            value = value.substring(0, 19);
        }
        return LocalDateTime.parse(value, A_STOCK_TIME_FORMATTER);
    }

    private record SecurityRef(String stockCode, String stockName) {
    }
}
