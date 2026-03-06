package com.dawei.service.impl;

import cn.hutool.json.JSONUtil;
import com.dawei.entity.*;
import com.dawei.enums.StockTag;
import com.dawei.service.RssService;
import com.dawei.service.StockService;
import com.dawei.utils.*;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.annotation.Resource;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
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
public class RssServiceImpl implements RssService {
    @Resource
    private StockService stockService;



    @Resource
    private TransApi transApi;

    @Resource
    private DingTalkApi dingTalkApi;

    @Resource
    private WeComApi weComApi;


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

//            transApi.testEnv();

            // 判断股票异动信息是否已存在，如果存在则不进行保存的操作
            if (stockService.isStockNewsExist(stockCode, stockNews.getLink())) {
                System.out.println("股票代码为【" + stockCode + "】的已存在，跳过。。。");
                continue;
            }


            // 使用百度翻译SDK调用其API对英文标题进行翻译（翻译为中文）
            String finalTitleZh = "";

            String result = transApi.getTransResult(titleEn, "en", "zh");
//            System.out.println("百度翻译调用的结果 result为： " +  result);
            BaiduTransEntity transEntity = JSONUtil.toBean(result, BaiduTransEntity.class);
            if (transEntity != null) {
                List<BaiduTransEntity.TransResult> transResultList = transEntity.getTrans_result();
                if (transResultList == null || transResultList.isEmpty() || transResultList.size() <= 0) {
                    continue;
                }
                finalTitleZh = transResultList.get(0).getDst();
            }

            // 处理股票标题，翻译为中文
            stockNews.setTitleZh(finalTitleZh);

            // 获得股票异动信息的标签
            try {
                List<String> tagsList = StockTitanCrawler.getTags(titleEn);
                stockNews.setTags(getTagsZh(tagsList));
            } catch (Exception e) {
//                throw new RuntimeException(e);
                // 此处因为频繁访问页面抓取数据，可能导致429的反爬虫异常，无需处理，tags无所谓，可以直接设为空值
                stockNews.setTags("");
            }

            System.out.println(stockNews.toString());
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

            System.out.println(stockMsg.toString());
            stockMsgList.add(stockMsg);
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

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(A_STOCK_NOTICE_URL);

        // 模拟浏览器（很重要）
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        httpGet.setHeader("Accept", "application/json");

        String json = httpClient.execute(httpGet,
                response -> EntityUtils.toString(response.getEntity(), "UTF-8"));

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
