package com.dawei.service.impl;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.service.AISummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @ClassName AISummaryServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description AI 总结服务实现类
 **/
@Slf4j
@Service
public class AISummaryServiceImpl implements AISummaryService {

    private final ChatClient chatClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 美股总结提示词模板
    private static final String US_STOCK_SUMMARY_PROMPT = """
        你是一位专业的股票分析师，擅长分析美股异动情况。
        
        请根据以下过去24小时内异动最频繁的前5只美股数据，生成一份专业的盘前早报总结：
        
        【股票数据】
        {stockData}
        
        【输出要求】
        1. 用简洁专业的语言总结每只股票的核心看点
        2. 分析异动背后的可能原因（基于标题中的关键词）
        3. 突出显示高频异动股票
        4. 总结控制在300字以内
        5. 语气专业、客观，适合投资参考
        6. 每只股票用一句话概括
        
        请直接输出总结内容，不需要标题。
        """;

    // A股总结提示词模板
    private static final String A_STOCK_SUMMARY_PROMPT = """
        你是一位专业的A股分析师，擅长分析A股公告异动情况。
        
        请根据以下过去24小时内公告异动最频繁的前5只A股数据，生成一份专业的盘前早报总结：
        
        【股票数据】
        {stockData}
        
        【输出要求】
        1. 用简洁专业的语言总结每只股票的公告看点
        2. 分析公告类型和可能的市场影响
        3. 突出显示高频公告股票
        4. 总结控制在300字以内
        5. 语气专业、客观，适合投资参考
        6. 每只股票用一句话概括
        
        请直接输出总结内容，不需要标题。
        """;

    // 美股盘前早报 Markdown 生成提示词（新模板）
    private static final String US_MORNING_REPORT_PROMPT = """
        【角色设定】
        你是一位专业的财经资讯分析师，负责将美股异动数据整理成适合企业微信推送的 Markdown 格式报告。
        
        【任务描述】
        根据提供的美股异动数据，生成符合规范的企业微信推送文案。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源数量：全网财经资讯源
        
        异动股票数据：
        {stockData}
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 每只股票的核心逻辑分析控制在 40-80 字
        
        【格式规范】
        - 标题格式：🌅 AI 盘前异动雷达 | 日期
        - 开头统计行：扫描时长、数据源数量
        - 股票条目格式：
          1. 股票名 (代码) | 🇺🇸 美股
          📊 异动频次：<font color="颜色">次数 次 (等级)</font>
          🧠 AI 核心逻辑：简述
        - 颜色选择规则：
          * frequency >= 25: color="warning", 等级="极度活跃"
          * 15 <= frequency < 25: color="info", 等级="高度活跃"
          * 8 <= frequency < 15: color="success", 等级="中度活跃"
          * frequency < 8: color="comment", 等级="轻度活跃"
        - 互动引导：固定格式
        - 免责声明：固定格式，使用 <font color="comment">
        
        【示例输出】
        🌅 AI 盘前异动雷达 | 2026-03-07
        
        过去 24 小时内，系统共扫描全网财经资讯源，以下标的爆发密集异动，请注意盘前风险与机会：
        
        1. 英伟达 (NVDA) | 🇺🇸 美股
        📊 异动频次：<font color="warning">28 次 (极度活跃)</font>
        🧠 AI 核心逻辑：盘后传出新一代 AI 芯片产能大幅扩充，且多家华尔街投行连夜上调评级，产业链看多情绪剧烈升温。
        
        2. 特斯拉 (TSLA) | 🇺🇸 美股
        📊 异动频次：<font color="info">19 次 (高度活跃)</font>
        🧠 AI 核心逻辑：FSD 新版本推送引发市场热议，同时马斯克透露机器人业务最新进展，多头情绪持续发酵。
        
        3. 苹果 (AAPL) | 🇺🇸 美股
        📊 异动频次：<font color="info">14 次 (高度活跃)</font>
        🧠 AI 核心逻辑：供应链传出 iPhone 新机型订单超预期，叠加回购计划推进，支撑股价韧性。
        
        💡 AI 深度查股：
        想看上述股票的具体新闻源？或者查询你的自选股？
        👉 请在群内直接发送：@机器人 分析 [股票代码]
        
        <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    // A股盘前早报 Markdown 生成提示词（新模板）
    private static final String A_MORNING_REPORT_PROMPT = """
        【角色设定】
        你是一位专业的财经资讯分析师，负责将A股异动数据整理成适合企业微信推送的 Markdown 格式报告。
        
        【任务描述】
        根据提供的A股异动数据，生成符合规范的企业微信推送文案。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源数量：全网财经资讯源
        
        异动股票数据：
        {stockData}
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 每只股票的核心逻辑分析控制在 40-80 字
        
        【格式规范】
        - 标题格式：🌅 AI 盘前异动雷达 | 日期
        - 开头统计行：扫描时长、数据源数量
        - 股票条目格式：
          1. 股票名 (代码) | 🇨🇳 A股
          📊 异动频次：<font color="颜色">次数 次 (等级)</font>
          🧠 AI 核心逻辑：简述
        - 颜色选择规则：
          * frequency >= 25: color="warning", 等级="极度活跃"
          * 15 <= frequency < 25: color="info", 等级="高度活跃"
          * 8 <= frequency < 15: color="success", 等级="中度活跃"
          * frequency < 8: color="comment", 等级="轻度活跃"
        - 互动引导：固定格式
        - 免责声明：固定格式，使用 <font color="comment">
        
        【示例输出】
        🌅 AI 盘前异动雷达 | 2026-03-07
        
        过去 24 小时内，系统共扫描全网财经资讯源，以下标的爆发密集异动，请注意盘前风险与机会：
        
        1. 赛力斯 (601127) | 🇨🇳 A股
        📊 异动频次：<font color="warning">28 次 (极度活跃)</font>
        🧠 AI 核心逻辑：受最新车型单月交付量创历史新高，以及智驾系统大版本 OTA 升级的新闻密集催化。
        
        2. 浪潮信息 (000977) | 🇨🇳 A股
        📊 异动频次：<font color="info">19 次 (高度活跃)</font>
        🧠 AI 核心逻辑：受国内算力基础设施集中招标大单落地预期影响，硬件服务器板块整体异动。
        
        3. 比亚迪 (002594) | 🇨🇳 A股
        📊 异动频次：<font color="info">14 次 (高度活跃)</font>
        🧠 AI 核心逻辑：销量数据超预期叠加出口业务突破，新能源龙头持续获得资金关注。
        
        💡 AI 深度查股：
        想看上述股票的具体新闻源？或者查询你的自选股？
        👉 请在群内直接发送：@机器人 分析 [股票代码]
        
        <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    public AISummaryServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String summarizeUSStocks(List<USStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return "过去24小时内暂无美股异动数据。";
        }

        String stockData = formatUSStockData(stockList);
        String prompt = US_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData);

        try {
            log.info("开始调用AI总结美股数据，共 {} 只股票", stockList.size());
            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("AI总结美股数据完成");
            return summary;
        } catch (Exception e) {
            log.error("AI总结美股数据失败: {}", e.getMessage(), e);
            return generateFallbackSummary(stockList, "美股");
        }
    }

    @Override
    public String summarizeAStocks(List<AStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return "过去24小时内暂无A股异动数据。";
        }

        String stockData = formatAStockData(stockList);
        String prompt = A_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData);

        try {
            log.info("开始调用AI总结A股数据，共 {} 只股票", stockList.size());
            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("AI总结A股数据完成");
            return summary;
        } catch (Exception e) {
            log.error("AI总结A股数据失败: {}", e.getMessage(), e);
            return generateFallbackSummaryA(stockList);
        }
    }

    @Override
    public String generateUSMorningReportMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "美股");
        }

        String stockData = formatUSStockAlertData(stockAlertList);
        String prompt = US_MORNING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成美股盘前早报 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("美股盘前早报 Markdown 生成完成");
            return markdown;
        } catch (Exception e) {
            log.error("生成美股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateFallbackMarkdown(stockAlertList, reportDate, "美股", "🇺🇸");
        }
    }

    @Override
    public String generateAMorningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "A股");
        }

        String stockData = formatAStockAlertData(stockAlertList);
        String prompt = A_MORNING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成A股盘前早报 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("A股盘前早报 Markdown 生成完成");
            return markdown;
        } catch (Exception e) {
            log.error("生成A股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateFallbackMarkdown(stockAlertList, reportDate, "A股", "🇨🇳");
        }
    }

    /**
     * 格式化美股数据（旧方法兼容）
     */
    private String formatUSStockData(List<USStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockList.size(); i++) {
            USStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append("\n");
            sb.append("   标题(英): ").append(stock.getTitle()).append("\n");
            sb.append("   标题(中): ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   标签: ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("   时间: ").append(stock.getPubDateBj() != null ? stock.getPubDateBj().format(DATE_FORMATTER) : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化A股数据（旧方法兼容）
     */
    private String formatAStockData(List<AStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append(" (").append(stock.getStockName()).append(")\n");
            sb.append("   标题: ").append(stock.getTitle()).append("\n");
            sb.append("   类型: ").append(stock.getTag()).append("\n");
            sb.append("   时间: ").append(stock.getPubDate() != null ? stock.getPubDate().format(DATE_FORMATTER) : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化美股异动数据（包含频次）
     */
    private String formatUSStockAlertData(List<StockAlertDTO<USStockRss>> stockAlertList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<USStockRss> dto = stockAlertList.get(i);
            USStockRss stock = dto.getStock();
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append("\n");
            sb.append("   名称: ").append(stock.getStockCode()).append("\n");
            sb.append("   异动频次: ").append(dto.getFrequency()).append(" 次\n");
            sb.append("   活跃度: ").append(dto.getActivityLevel()).append("\n");
            sb.append("   颜色标签: ").append(dto.getColorTag()).append("\n");
            sb.append("   标题(英): ").append(stock.getTitle()).append("\n");
            sb.append("   标题(中): ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   标签: ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化A股异动数据（包含频次）
     */
    private String formatAStockAlertData(List<StockAlertDTO<AStockRss>> stockAlertList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<AStockRss> dto = stockAlertList.get(i);
            AStockRss stock = dto.getStock();
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append(" (").append(stock.getStockName()).append(")\n");
            sb.append("   名称: ").append(stock.getStockName()).append("\n");
            sb.append("   异动频次: ").append(dto.getFrequency()).append(" 次\n");
            sb.append("   活跃度: ").append(dto.getActivityLevel()).append("\n");
            sb.append("   颜色标签: ").append(dto.getColorTag()).append("\n");
            sb.append("   标题: ").append(stock.getTitle()).append("\n");
            sb.append("   类型: ").append(stock.getTag()).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成美股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummary(List<USStockRss> stockList, String marketType) {
        StringBuilder sb = new StringBuilder();
        sb.append("过去24小时内").append(marketType).append("异动TOP5：\n\n");
        for (int i = 0; i < stockList.size(); i++) {
            USStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("**");
            sb.append(" - ").append(stock.getTitleZh() != null ? stock.getTitleZh() : stock.getTitle());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成A股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummaryA(List<AStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        sb.append("过去24小时内A股公告异动TOP5：\n\n");
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("** (").append(stock.getStockName()).append(")");
            sb.append(" - ").append(stock.getTitle());
            sb.append("【").append(stock.getTag()).append("】");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建无数据时的 Markdown 消息
     */
    private String buildNoDataMarkdown(String reportDate, String market) {
        return "🌅 AI 盘前异动雷达 | " + reportDate + "\n\n" +
               "过去 24 小时内，系统共扫描全网财经资讯源。\n\n" +
               "<font color=\"warning\">⚠️ 暂无" + market + "异动数据</font>\n\n" +
               "💡 AI 深度查股：\n" +
               "想看具体股票分析？请在群内直接发送：@机器人 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>";
    }

    /**
     * 生成降级 Markdown（当AI调用失败时使用）
     */
    private <T> String generateFallbackMarkdown(List<StockAlertDTO<T>> stockAlertList, String reportDate, 
                                                  String market, String flag) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌅 AI 盘前异动雷达 | ").append(reportDate).append("\n\n");
        sb.append("过去 24 小时内，系统共扫描全网财经资讯源，以下标的爆发密集异动，请注意盘前风险与机会：\n\n");

        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<T> dto = stockAlertList.get(i);
            String stockCode;
            String stockName = null;
            String title = null;
            String tag = null;

            if (dto.getStock() instanceof USStockRss) {
                USStockRss stock = (USStockRss) dto.getStock();
                stockCode = stock.getStockCode();
                stockName = stockCode;
                title = stock.getTitleZh() != null ? stock.getTitleZh() : stock.getTitle();
                tag = stock.getTags();
            } else if (dto.getStock() instanceof AStockRss) {
                AStockRss stock = (AStockRss) dto.getStock();
                stockCode = stock.getStockCode();
                stockName = stock.getStockName();
                title = stock.getTitle();
                tag = stock.getTag();
            } else {
                stockCode = "未知";
            }

            String displayName = stockName != null ? stockName : stockCode;
            sb.append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ").append(flag).append(" ").append(market).append("\n");
            sb.append("📊 异动频次：<font color=\"").append(dto.getColorTag()).append("\">").append(dto.getFrequency()).append(" 次 (").append(dto.getActivityLevel()).append(")</font>\n");
            sb.append("🧠 AI 核心逻辑：").append(title != null ? title : "暂无详细分析");
            if (tag != null && !tag.isEmpty()) {
                sb.append(" [").append(tag).append("]");
            }
            sb.append("\n\n");
        }

        sb.append("💡 AI 深度查股：\n");
        sb.append("想看上述股票的具体新闻源？或者查询你的自选股？\n");
        sb.append("👉 请在群内直接发送：@机器人 分析 [股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>");

        return sb.toString();
    }
}
