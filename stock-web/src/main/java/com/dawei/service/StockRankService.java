package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;

import java.util.List;

/**
 * @ClassName StockRankService
 * @Author dawei
 * @Version 1.0
 * @Description 股票异动排名服务接口
 **/
public interface StockRankService {

    /**
     * 获取过去24小时内美股异动排名前N的股票
     * @param limit 返回数量
     * @return 美股异动排名列表
     */
    List<USStockRss> getUSTopNStocks(int limit);

    /**
     * 获取过去24小时内A股公告异动排名前N的股票
     * @param limit 返回数量
     * @return A股公告异动排名列表
     */
    List<AStockRss> getATopNStocks(int limit);

    /**
     * 获取过去24小时内美股异动排名前N的股票（包含异动频次）
     * @param limit 返回数量
     * @return 美股异动排名列表（包含频次统计）
     */
    List<StockAlertDTO<USStockRss>> getUSTopNStocksWithFrequency(int limit);

    /**
     * 获取过去24小时内A股公告异动排名前N的股票（包含异动频次）
     * @param limit 返回数量
     * @return A股公告异动排名列表（包含频次统计）
     */
    List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit);
}
