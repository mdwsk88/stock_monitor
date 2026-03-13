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
    @Resource
    private StockService stockService;

    @Resource
    private DingTalkApi dingTalkApi;

    @Resource
    private WeComApi weComApi;

    @Resource
    private RestTemplate restTemplate;


    public static final String RSS_URL = "https://www.stocktitan.net/rss";



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

                // 判断股票异动信息是否已存在，如果存在则不进行保存的操作
                if (stockService.isStockNewsExist(stockCode, stockNews.getLink())) {
                    log.info("股票代码为【{}】的已存在，跳过。。。", stockCode);
                    continue;
                }

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
                stockService.saveStockNews(stockNews);

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

        if (!stockMsgList.isEmpty()) {
            //dingTalkApi.sendTextMessage(dingTalkApi.formatStockInfoFromList(stockMsgList));
            weComApi.sendMarkdownMessage(weComApi.formatStockInfoFromList(stockMsgList), WeComApi.MarketType.US);
            //weComApi.sendTextMessage(weComApi.formatStockInfoTextFromList(stockMsgList), WeComApi.MarketType.US);
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

    /**
     * A股公告API地址
     * 原地址：https://data.eastmoney.com/notices/hsa/7.html
     */
    public static final String A_STOCK_NOTICE_URL = "https://np-anotice-stock.eastmoney.com/api/security/ann" +
            "?sr=-1&page_size=100&page_index=1&ann_type=SHA,CYB,SZA,BJA,INV&client_source=web&f_node=3,5,6&s_node=0";

    @Override
    public void fetchAndSaveAStockNotices() throws Exception {
        List<AStockMsg> aStockMsgList = new ArrayList<>();

        // 【优化】使用注入的 RestTemplate，避免 HTTP 连接泄漏
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                A_STOCK_NOTICE_URL, HttpMethod.GET, entity, String.class);
        String json = response.getBody();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode list = root.path("data").path("list");

        // displayTime 格式：2026-02-14 21:23:18:673 (冒号分隔的毫秒)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (JsonNode node : list) {
            String stockCode = node.get("codes").get(0).get("stock_code").asText();
            String stockName = node.get("codes").get(0).get("short_name").asText();
            String title = node.get("title").asText();
            String tag = node.get("columns").get(0).get("column_name").asText();
            String displayTime = node.get("display_time").asText();

            // 处理时间格式：去掉毫秒部分（:673），只保留 yyyy-MM-dd HH:mm:ss
            if (displayTime != null && displayTime.length() > 19) {
                displayTime = displayTime.substring(0, 19);
            }

            // 解析公告时间
            LocalDateTime pubDate = LocalDateTime.parse(displayTime, formatter);

            // 判断公告是否已存在
            if (stockService.isAStockNewsExist(stockCode, title, displayTime)) {
                System.out.println("A股公告已存在，跳过：【" + stockCode + " - " + stockName + "】" + title);
                continue;
            }

            // 创建并保存A股公告
            AStockRss aStockRss = new AStockRss();
            aStockRss.setId(UUID.randomUUID().toString().replace("-", ""));
            aStockRss.setStockCode(stockCode);
            aStockRss.setStockName(stockName);
            aStockRss.setTitle(title);
            aStockRss.setTag(tag);
            aStockRss.setPubDate(pubDate);
            // 设置当前插入时间
            aStockRss.setCreateTime(LocalDateTime.now());
            // 东方财富公告链接
            aStockRss.setLink("https://data.eastmoney.com/notices/detail/" + stockCode + "/" + node.get("art_code").asText() + ".html");

            stockService.saveAStockNews(aStockRss);

            System.out.println("A股公告保存成功：" + aStockRss);

            // 统计该股票在24小时、3天、1周内的公告次数
            Long counts24Hour = stockService.getAStockNoticeCounts(stockCode,
                    GMTDateConverter.minus24Hour(pubDate),
                    GMTDateConverter.plus1Minute(pubDate));
            Long counts3Day = stockService.getAStockNoticeCounts(stockCode,
                    GMTDateConverter.minus3Day(pubDate),
                    GMTDateConverter.plus1Minute(pubDate));
            Long counts1Week = stockService.getAStockNoticeCounts(stockCode,
                    GMTDateConverter.minus1Week(pubDate),
                    GMTDateConverter.plus1Minute(pubDate));

            // 添加到消息列表
            AStockMsg aStockMsg = new AStockMsg();
            aStockMsg.setStockCode(stockCode);
            aStockMsg.setStockName(stockName);
            aStockMsg.setTitle(title);
            aStockMsg.setTag(tag);
            aStockMsg.setPubDate(displayTime);
            aStockMsg.setCounts24Hour(counts24Hour.intValue());
            aStockMsg.setCounts3Day(counts3Day.intValue());
            aStockMsg.setCounts1Week(counts1Week.intValue());

            aStockMsgList.add(aStockMsg);
        }

        // 发送消息通知
        if (!aStockMsgList.isEmpty()) {
            weComApi.sendMarkdownMessage(weComApi.formatAStockInfoFromList(aStockMsgList), WeComApi.MarketType.A);
        }
    }

}
