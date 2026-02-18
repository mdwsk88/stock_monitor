package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.USStockRss;
import com.rometools.rome.feed.synd.SyndEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @ClassName StockService
 * @Author dawei
 * @Version 1.0
 * @Description StockService
 **/
public interface StockService {

    // ============== 美股相关方法 ==============
    public void saveStockNews(USStockRss stockNews);

    public Boolean isStockNewsExist(String stockCode, String link);

    public Long getStockUnusualCounts(USStockRss stockNews, LocalDateTime startDate, LocalDateTime endDate);

    // ============== A股相关方法 ==============
    public void saveAStockNews(AStockRss aStockNews);

    public Boolean isAStockNewsExist(String stockCode, String title, String pubDate);

    public Long getAStockNoticeCounts(String stockCode, LocalDateTime startDate, LocalDateTime endDate);

}
