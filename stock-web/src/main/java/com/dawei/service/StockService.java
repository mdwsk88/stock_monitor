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
    boolean saveStockNewsIfAbsent(USStockRss stockNews);

    public Long getStockUnusualCounts(USStockRss stockNews, LocalDateTime startDate, LocalDateTime endDate);

    // ============== A股相关方法 ==============
    boolean saveAStockNewsIfAbsent(AStockRss aStockNews);

    public Long getAStockNoticeCounts(String stockCode, LocalDateTime startDate, LocalDateTime endDate);

}
