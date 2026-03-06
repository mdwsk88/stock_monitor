package com.dawei.scheduler;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.service.AISummaryService;
import com.dawei.service.StockRankService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @ClassName MorningReportScheduler
 * @Author dawei
 * @Version 2.0
 * @Description 盘前早报定时任务 - 每天早上8:30推送AI总结的异动雷达（新模板格式）
 **/
@Slf4j
@Component
public class MorningReportScheduler {

    private final StockRankService stockRankService;
    private final AISummaryService aiSummaryService;
    private final WeComApi weComApi;

    private static final int TOP_N = 5;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public MorningReportScheduler(StockRankService stockRankService, 
                                   AISummaryService aiSummaryService, 
                                   WeComApi weComApi) {
        this.stockRankService = stockRankService;
        this.aiSummaryService = aiSummaryService;
        this.weComApi = weComApi;
    }

    /**
     * 美股盘前早报 - 每天早上8:30执行
     *  cron表达式: 0 30 8 * * ?
     *  注意：美股盘前早报需要在北京时间晚上或第二天早上推送（美股交易时间是北京时间晚上9:30-凌晨4:00）
     *  这里设置为每天早上8:30推送前一天的美股异动情况
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void pushUSMorningReport() {
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        log.info("【美股盘前早报】开始执行，日期: {}", today);

        try {
            // 1. 查询过去24小时内美股异动排名前5的股票（包含频次统计）
            List<StockAlertDTO<USStockRss>> topStocks = stockRankService.getUSTopNStocksWithFrequency(TOP_N);

            if (topStocks.isEmpty()) {
                log.warn("【美股盘前早报】过去24小时内无美股异动数据");
                String noDataMsg = buildNoDataMessage("美股", today);
                weComApi.sendMarkdownMessage(noDataMsg, WeComApi.MarketType.US);
                return;
            }

            // 2. 使用AI生成完整的企业微信 Markdown 消息（新模板格式）
            String markdownMessage = aiSummaryService.generateUSMorningReportMarkdown(topStocks, today);

            // 3. 直接推送AI生成的消息
            weComApi.sendMarkdownMessage(markdownMessage, WeComApi.MarketType.US);

            log.info("【美股盘前早报】推送成功，共 {} 只股票", topStocks.size());

        } catch (Exception e) {
            log.error("【美股盘前早报】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildErrorMessage("美股", today, e.getMessage());
            weComApi.sendMarkdownMessage(errorMsg, WeComApi.MarketType.US);
        }
    }

    /**
     * A股盘前早报 - 每天早上8:30执行
     *  cron表达式: 0 30 8 * * ?
     *  A股交易时间：周一至周五 9:30-11:30, 13:00-15:00
     *  早上8:30推送，为开盘前提供参考
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void pushAMorningReport() {
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        log.info("【A股盘前早报】开始执行，日期: {}", today);

        try {
            // 1. 查询过去24小时内A股公告异动排名前5的股票（包含频次统计）
            List<StockAlertDTO<AStockRss>> topStocks = stockRankService.getATopNStocksWithFrequency(TOP_N);

            if (topStocks.isEmpty()) {
                log.warn("【A股盘前早报】过去24小时内无A股公告数据");
                String noDataMsg = buildNoDataMessage("A股", today);
                weComApi.sendMarkdownMessage(noDataMsg, WeComApi.MarketType.A);
                return;
            }

            // 2. 使用AI生成完整的企业微信 Markdown 消息（新模板格式）
            String markdownMessage = aiSummaryService.generateAMorningReportMarkdown(topStocks, today);

            // 3. 直接推送AI生成的消息
            weComApi.sendMarkdownMessage(markdownMessage, WeComApi.MarketType.A);

            log.info("【A股盘前早报】推送成功，共 {} 只股票", topStocks.size());

        } catch (Exception e) {
            log.error("【A股盘前早报】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildErrorMessage("A股", today, e.getMessage());
            weComApi.sendMarkdownMessage(errorMsg, WeComApi.MarketType.A);
        }
    }

    /**
     * 构建无数据时的消息
     */
    private String buildNoDataMessage(String market, String today) {
        String flag = market.equals("美股") ? "🇺🇸" : "🇨🇳";
        return "🌅 AI 盘前异动雷达 | " + today + "\n\n" +
               "过去 24 小时内，系统共扫描全网财经资讯源。\n\n" +
               "<font color=\"warning\">⚠️ 暂无" + flag + " " + market + "异动数据</font>\n\n" +
               "💡 AI 深度查股：\n" +
               "想看具体股票分析？请在群内直接发送：@机器人 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>";
    }

    /**
     * 构建错误消息
     */
    private String buildErrorMessage(String market, String today, String errorMsg) {
        String flag = market.equals("美股") ? "🇺🇸" : "🇨🇳";
        return "🌅 AI 盘前异动雷达 | " + today + "\n\n" +
               "过去 24 小时内，系统共扫描全网财经资讯源。\n\n" +
               "<font color=\"warning\">❌ 数据获取失败: " + errorMsg + "</font>\n\n" +
               "💡 AI 深度查股：\n" +
               "想看具体股票分析？请在群内直接发送：@机器人 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>";
    }
}
