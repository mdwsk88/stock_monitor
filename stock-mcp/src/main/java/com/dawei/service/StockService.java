package com.dawei.service;

import com.dawei.entity.StockCounts;
import com.dawei.entity.USStockRss;

import java.util.List;

/**
 * @ClassName RssService
 * @Author 风间影月
 * @Version 1.0
 * @Description RssService
 **/
public interface StockService {

    /**
     * @Description: 根据股票代码查询股票数据
     * @Author 风间影月
     * @param stockCode
     */
    public List<USStockRss> queryStock(String stockCode);

    /**
     * @Description: 查询时间段内的股票数据
     * @Author 风间影月
     * @param stockCode
     * @param startDate
     * @param endDate
     * @return List<USStockRss>
     */
    public List<USStockRss> queryStockBetweenDate(String stockCode, String startDate, String endDate);

    /**
     * @Description: 查询指定日期段内异动次数超过指定次数
     * @Author 风间影月
     * @param targetCounts
     * @param startDate
     * @param endDate
     * @return List<StockCounts>
     */
    public List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate);

    /**
     * @Description: 根据标题关键字查询股票数据
     * @Author 风间影月
     * @param titleKeywords
     * @return List<USStockRss>
     */
    public List<USStockRss> queryStockByTitleKeywords(List<String> titleKeywords);
}
