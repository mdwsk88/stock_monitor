package com.dawei.service;

import com.dawei.entity.USStockRss;
import com.rometools.rome.feed.synd.SyndEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @ClassName StockService
 * @Author 风间影月
 * @Version 1.0
 * @Description StockService
 **/
public interface StockService {

    public void saveStockNews(USStockRss stockNews);

    public Boolean isStockNewsExist(String stockCode, String link);

    public Long getStockUnusualCounts(USStockRss stockNews, LocalDateTime startDate, LocalDateTime endDate);

}
