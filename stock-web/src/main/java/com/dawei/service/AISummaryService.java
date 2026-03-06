package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;

import java.util.List;

/**
 * @ClassName AISummaryService
 * @Author dawei
 * @Version 1.0
 * @Description AI 总结服务接口
 **/
public interface AISummaryService {

    /**
     * 对美股异动数据进行AI总结
     * @param stockList 美股异动数据列表
     * @return 总结后的文本
     */
    String summarizeUSStocks(List<USStockRss> stockList);

    /**
     * 对A股异动数据进行AI总结
     * @param stockList A股异动数据列表
     * @return 总结后的文本
     */
    String summarizeAStocks(List<AStockRss> stockList);

    /**
     * 生成美股盘前早报的企业微信 Markdown 格式消息（新模板）
     * @param stockAlertList 美股异动数据列表（包含频次）
     * @param reportDate 报告日期
     * @return 完整的企业微信 Markdown 消息
     */
    String generateUSMorningReportMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate);

    /**
     * 生成A股盘前早报的企业微信 Markdown 格式消息（新模板）
     * @param stockAlertList A股异动数据列表（包含频次）
     * @param reportDate 报告日期
     * @return 完整的企业微信 Markdown 消息
     */
    String generateAMorningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate);
}
