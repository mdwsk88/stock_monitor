package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;

import java.util.List;

/**
 * @ClassName AStockService
 * @Author dawei
 * @Version 1.0
 * @Description A股数据服务接口
 **/
public interface AStockService {

    /**
     * 根据股票代码查询股票数据
     */
    List<AStockRss> queryStock(String stockCode);

    /**
     * 根据股票名称查询股票数据
     */
    List<AStockRss> queryStockByName(String stockName);

    /**
     * 查询时间段内的股票数据
     */
    List<AStockRss> queryStockBetweenDate(String stockCode, String startDate, String endDate);

    /**
     * 查询指定日期段内异动次数超过指定次数的股票
     */
    List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate);

    /**
     * 根据标题关键字查询股票数据
     */
    List<AStockRss> queryStockByTitleKeywords(List<String> titleKeywords);

    /**
     * 根据股票名称关键字查询
     */
    List<AStockRss> queryStockByNameKeywords(List<String> nameKeywords);

}
