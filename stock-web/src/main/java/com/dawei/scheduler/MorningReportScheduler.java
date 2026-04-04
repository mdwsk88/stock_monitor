package com.dawei.scheduler;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.service.AReportFusionService;
import com.dawei.service.AISummaryService;
import com.dawei.service.StockRankService;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final AReportFusionService aReportFusionService;
    private final AISummaryService aiSummaryService;
    private final WeComApi weComApi;
    private final PushLanguageService pushLanguageService;
    @Value("${stock.push.us-enabled:false}")
    private boolean usPushEnabled;
    @Value("${stock.runtime.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    private static final int US_TOP_N = 5;
    private static final int A_TOP_N = 8;
    private static final int A_MACRO_THEME_LIMIT = 3;
    private static final int A_RESONANCE_LIMIT = 3;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final LocalTime A_MORNING_WINDOW_START = LocalTime.of(8, 0);
    private static final LocalTime A_MORNING_WINDOW_END = LocalTime.of(9, 40);
    private static final LocalTime A_EVENING_WINDOW_START = LocalTime.of(15, 0);
    private static final LocalTime A_EVENING_WINDOW_END = LocalTime.of(16, 30);
    private static final LocalTime A_POST_CLOSE_WINDOW_START = LocalTime.of(17, 30);
    private static final LocalTime A_POST_CLOSE_WINDOW_END = LocalTime.of(19, 30);
    private static final LocalTime US_MORNING_WINDOW_START = LocalTime.of(7, 0);
    private static final LocalTime US_MORNING_WINDOW_END = LocalTime.of(9, 0);
    private static final LocalTime US_EVENING_WINDOW_START = LocalTime.of(20, 0);
    private static final LocalTime US_EVENING_WINDOW_END = LocalTime.of(21, 30);

    public MorningReportScheduler(StockRankService stockRankService,
                                  AReportFusionService aReportFusionService,
                                  AISummaryService aiSummaryService,
                                  WeComApi weComApi) {
        this(stockRankService, aReportFusionService, aiSummaryService, weComApi, new PushLanguageService());
    }

    @Autowired
    public MorningReportScheduler(StockRankService stockRankService,
                                  AReportFusionService aReportFusionService,
                                  AISummaryService aiSummaryService,
                                  WeComApi weComApi,
                                  PushLanguageService pushLanguageService) {
        this.stockRankService = stockRankService;
        this.aReportFusionService = aReportFusionService;
        this.aiSummaryService = aiSummaryService;
        this.weComApi = weComApi;
        this.pushLanguageService = pushLanguageService;
    }

    /**
     * 美股早报（隔夜复盘）- 每天早上7:30执行（北京时间）
     *  cron表达式: 0 30 7 * * ?
     *  美股收盘时间：北京时间次日凌晨4:00
     *  早上7:30推送，复盘昨夜美股走势
     *  
     *  数据提取范围：过去12小时（昨晚20:00到今早8:00）
     */
    @Scheduled(cron = "0 30 7 * * ?")
    public void pushUSMorningReport() {
        if (skipScheduledRuntime("美股早报（隔夜复盘）")) {
            return;
        }
        runUSMorningReport(false);
    }

    public void pushUSMorningReportManually() {
        runUSMorningReport(true);
    }

    private void runUSMorningReport(boolean manualTrigger) {
        if (skipUSPush("美股早报（隔夜复盘）")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (shouldSkipScheduledWindow(now, manualTrigger, US_MORNING_WINDOW_START, US_MORNING_WINDOW_END, "美股早报（隔夜复盘）")) {
            return;
        }
        String today = now.format(DATE_FORMATTER);
        log.info("【美股早报（隔夜复盘）】开始执行，日期: {}，触发方式: {}", today, manualTrigger ? "手动" : "定时");

        try {
            // 数据提取范围：过去12小时（昨晚20:00到今早8:00）
            LocalDateTime startTime = now.minusDays(1).withHour(20).withMinute(0).withSecond(0);
            LocalDateTime endTime = now.withHour(8).withMinute(0).withSecond(0);
            log.info("【美股早报（隔夜复盘）】数据提取范围: {} 至 {}", startTime, endTime);
            
            // 1. 查询指定时间范围内美股异动排名前5的股票（包含频次统计）
            List<StockAlertDTO<USStockRss>> topStocks = stockRankService.getUSTopNStocksWithFrequency(US_TOP_N, startTime, endTime);

            if (topStocks.isEmpty()) {
                log.warn("【美股早报（隔夜复盘）】过去24小时内无美股异动数据");
                String noDataMsg = buildUSOvernightNoDataMessage(today);
                weComApi.sendMarkdownMessageAsync(noDataMsg, WeComApi.MarketType.US);
                return;
            }

            // 2. 使用AI生成完整的隔夜复盘 Markdown 消息
            String markdownMessage = aiSummaryService.generateUSOvernightRecapMarkdown(topStocks, today);

            // 3. 直接推送AI生成的消息
            weComApi.sendMarkdownMessageAsync(markdownMessage, WeComApi.MarketType.US);

            log.info("【美股早报（隔夜复盘）】推送成功，共 {} 只股票", topStocks.size());

        } catch (Exception e) {
            log.error("【美股早报（隔夜复盘）】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildUSOvernightErrorMessage(today, e.getMessage());
            weComApi.sendMarkdownMessageAsync(errorMsg, WeComApi.MarketType.US);
        }
    }

    /**
     * A股盘前早报 - 每天早上8:30执行（北京时间）
     *  cron表达式: 0 30 8 * * ?
     *  A股交易时间：周一至周五 9:30-11:30, 13:00-15:00
     *  早上8:30推送，为开盘前提供参考
     *  
     *  数据提取范围：过去24小时（昨天上午8:30到今天上午8:30）
     *  
     *  周末静默处理：周六、周日不推送，避免扰民
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void pushAMorningReport() {
        if (skipScheduledRuntime("A股盘前早报")) {
            return;
        }
        runAMorningReport(false);
    }

    public void pushAMorningReportManually() {
        runAMorningReport(true);
    }

    private void runAMorningReport(boolean manualTrigger) {
        LocalDateTime now = LocalDateTime.now();
        
        // 周末静默处理：A股周末不开盘，不发送早报
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (!manualTrigger && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            log.info("【A股盘前早报】今天是{}，股市休市，静默处理不发送早报", dayOfWeek);
            return;
        }
        if (shouldSkipScheduledWindow(now, manualTrigger, A_MORNING_WINDOW_START, A_MORNING_WINDOW_END, "A股盘前早报")) {
            return;
        }
        
        String today = now.format(DATE_FORMATTER);
        log.info("【A股盘前早报】开始执行，日期: {}，触发方式: {}", today, manualTrigger ? "手动" : "定时");

        try {
            // 数据提取范围：过去24小时（昨天上午8:30到今天上午8:30）
            LocalDateTime startTime = now.minusHours(24);
            LocalDateTime endTime = now;
            log.info("【A股盘前早报】数据提取范围: {} 至 {}", startTime, endTime);
            
            AReportFusionContext reportContext = aReportFusionService.buildContext(
                    startTime,
                    endTime,
                    A_TOP_N,
                    A_MACRO_THEME_LIMIT,
                    A_RESONANCE_LIMIT
            );
            log.info("【A股盘前早报】融合上下文: 公告候选={}，宏观主题={}，共振={}",
                    reportContext.getAlertCount(),
                    reportContext.getMacroThemeCount(),
                    reportContext.getResonanceCount());

            if (reportContext.isEmpty()) {
                log.warn("【A股盘前早报】过去24小时内无A股公告或宏观主题数据");
                String noDataMsg = buildNoDataMessage("A股", today);
                weComApi.sendMarkdownMessageAsync(noDataMsg, WeComApi.MarketType.A);
                return;
            }

            String markdownMessage = aiSummaryService.generateAMorningReportMarkdown(reportContext, today);

            weComApi.sendMarkdownMessageAsync(markdownMessage, WeComApi.MarketType.A);

            log.info("【A股盘前早报】推送成功，公告候选={}，宏观主题={}，共振={}",
                    reportContext.getAlertCount(),
                    reportContext.getMacroThemeCount(),
                    reportContext.getResonanceCount());

        } catch (Exception e) {
            log.error("【A股盘前早报】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildErrorMessage("A股", today, e.getMessage());
            weComApi.sendMarkdownMessageAsync(errorMsg, WeComApi.MarketType.A);
        }
    }

    /**
     * A股盘后复盘 - 每天下午15:30执行（北京时间）
     *  cron表达式: 0 30 15 * * ?
     *  A股交易时间：周一至周五 9:30-11:30, 13:00-15:00
     *  下午15:30推送，对当日盘面进行复盘解码
     *  
     *  数据提取范围：当天9:00到15:00（过去6小时）
     *  
     *  周末静默处理：周六、周日不推送
     */
    @Scheduled(cron = "0 30 15 * * ?")
    public void pushAEveningReport() {
        if (skipScheduledRuntime("A股盘后复盘")) {
            return;
        }
        runAEveningReport(false);
    }

    public void pushAEveningReportManually() {
        runAEveningReport(true);
    }

    private void runAEveningReport(boolean manualTrigger) {
        LocalDateTime now = LocalDateTime.now();
        
        // 周末静默处理：A股周末不开盘，不发送复盘
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (!manualTrigger && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            log.info("【A股盘后复盘】今天是{}，股市休市，静默处理不发送复盘", dayOfWeek);
            return;
        }
        if (shouldSkipScheduledWindow(now, manualTrigger, A_EVENING_WINDOW_START, A_EVENING_WINDOW_END, "A股盘后复盘")) {
            return;
        }
        
        String today = now.format(DATE_FORMATTER);
        log.info("【A股盘后复盘】开始执行，日期: {}，触发方式: {}", today, manualTrigger ? "手动" : "定时");

        try {
            // 数据提取范围：当天9:00到15:00（过去6小时）
            LocalDateTime startTime = now.withHour(9).withMinute(0).withSecond(0);
            LocalDateTime endTime = now.withSecond(0).withNano(0);
            log.info("【A股盘后复盘】数据提取范围: {} 至 {}", startTime, endTime);
            
            AReportFusionContext reportContext = aReportFusionService.buildContext(
                    startTime,
                    endTime,
                    A_TOP_N,
                    A_MACRO_THEME_LIMIT,
                    A_RESONANCE_LIMIT
            );
            log.info("【A股盘后复盘】融合上下文: 公告候选={}，宏观主题={}，共振={}",
                    reportContext.getAlertCount(),
                    reportContext.getMacroThemeCount(),
                    reportContext.getResonanceCount());

            if (reportContext.isEmpty()) {
                log.warn("【A股盘后复盘】日内无A股公告或宏观主题数据");
                String noDataMsg = buildAStockNoDataEveningMessage(today);
                weComApi.sendMarkdownMessageAsync(noDataMsg, WeComApi.MarketType.A);
                return;
            }

            String markdownMessage = aiSummaryService.generateAEveningReportMarkdown(reportContext, today);

            weComApi.sendMarkdownMessageAsync(markdownMessage, WeComApi.MarketType.A);

            log.info("【A股盘后复盘】推送成功，公告候选={}，宏观主题={}，共振={}",
                    reportContext.getAlertCount(),
                    reportContext.getMacroThemeCount(),
                    reportContext.getResonanceCount());

        } catch (Exception e) {
            log.error("【A股盘后复盘】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildAStockErrorEveningMessage(today, e.getMessage());
            weComApi.sendMarkdownMessageAsync(errorMsg, WeComApi.MarketType.A);
        }
    }

    @Scheduled(cron = "0 30 18 * * ?")
    public void pushAPostCloseRiskDigest() {
        if (skipScheduledRuntime("A股盘后风险速递")) {
            return;
        }
        runAPostCloseRiskDigest(false);
    }

    public void pushAPostCloseRiskDigestManually() {
        runAPostCloseRiskDigest(true);
    }

    private void runAPostCloseRiskDigest(boolean manualTrigger) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (!manualTrigger && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            log.info("【A股盘后风险速递】今天是{}，股市休市，静默处理", dayOfWeek);
            return;
        }
        if (shouldSkipScheduledWindow(now, manualTrigger, A_POST_CLOSE_WINDOW_START, A_POST_CLOSE_WINDOW_END, "A股盘后风险速递")) {
            return;
        }

        String today = now.format(DATE_FORMATTER);
        try {
            LocalDateTime startTime = now.withHour(15).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endTime = now.withSecond(0).withNano(0);
            AReportFusionContext reportContext = aReportFusionService.buildContext(
                    startTime,
                    endTime,
                    A_TOP_N,
                    A_MACRO_THEME_LIMIT,
                    A_RESONANCE_LIMIT
            );
            String markdown = reportContext.getRiskAlerts().isEmpty() && reportContext.getOpportunityAlerts().isEmpty()
                    ? buildAPostCloseNoDataMessage(today)
                    : buildAPostCloseRiskDigestMessage(today, reportContext);
            weComApi.sendMarkdownMessageAsync(markdown, WeComApi.MarketType.A);
            log.info("【A股盘后风险速递】推送成功，风险={}，机会={}",
                    reportContext.getRiskAlerts().size(),
                    reportContext.getOpportunityAlerts().size());
        } catch (Exception e) {
            log.error("【A股盘后风险速递】执行失败: {}", e.getMessage(), e);
            weComApi.sendMarkdownMessageAsync(buildAPostCloseErrorMessage(today, e.getMessage()), WeComApi.MarketType.A);
        }
    }

    /**
     * 美股夜报（盘前预警）- 每天晚上20:30执行（北京时间）
     *  cron表达式: 0 30 20 * * ?
     *  美股交易时间：北京时间晚上21:30-次日凌晨4:00
     *  晚上20:30推送，为今夜美股开盘提供预警
     *  
     *  数据提取范围：过去24小时（昨晚20:30到今晚20:30）
     */
    @Scheduled(cron = "0 30 20 * * ?")
    public void pushUSEveningReport() {
        if (skipScheduledRuntime("美股夜报（盘前预警）")) {
            return;
        }
        runUSEveningReport(false);
    }

    public void pushUSEveningReportManually() {
        runUSEveningReport(true);
    }

    private void runUSEveningReport(boolean manualTrigger) {
        if (skipUSPush("美股夜报（盘前预警）")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (shouldSkipScheduledWindow(now, manualTrigger, US_EVENING_WINDOW_START, US_EVENING_WINDOW_END, "美股夜报（盘前预警）")) {
            return;
        }
        String today = now.format(DATE_FORMATTER);
        log.info("【美股夜报（盘前预警）】开始执行，日期: {}，触发方式: {}", today, manualTrigger ? "手动" : "定时");

        try {
            // 数据提取范围：过去24小时（昨晚20:30到今晚20:30）
            LocalDateTime startTime = now.minusHours(24);
            LocalDateTime endTime = now;
            log.info("【美股夜报（盘前预警）】数据提取范围: {} 至 {}", startTime, endTime);
            
            // 1. 查询指定时间范围内美股异动排名前5的股票（包含频次统计）
            List<StockAlertDTO<USStockRss>> topStocks = stockRankService.getUSTopNStocksWithFrequency(US_TOP_N, startTime, endTime);

            if (topStocks.isEmpty()) {
                log.warn("【美股今夜雷达】过去24小时内无美股异动数据");
                String noDataMsg = buildUSStockNoDataEveningMessage(today);
                weComApi.sendMarkdownMessageAsync(noDataMsg, WeComApi.MarketType.US);
                return;
            }

            // 2. 使用AI生成完整的晚间雷达 Markdown 消息（晚间模板）
            String markdownMessage = aiSummaryService.generateUSEveningReportMarkdown(topStocks, today);

            // 3. 直接推送AI生成的消息
            weComApi.sendMarkdownMessageAsync(markdownMessage, WeComApi.MarketType.US);

            log.info("【美股今夜雷达】推送成功，共 {} 只股票", topStocks.size());

        } catch (Exception e) {
            log.error("【美股今夜雷达】执行失败: {}", e.getMessage(), e);
            String errorMsg = buildUSStockErrorEveningMessage(today, e.getMessage());
            weComApi.sendMarkdownMessageAsync(errorMsg, WeComApi.MarketType.US);
        }
    }

    public boolean isUsPushEnabled() {
        return usPushEnabled;
    }

    private boolean skipScheduledRuntime(String jobName) {
        if (schedulerEnabled) {
            return false;
        }
        log.info("【{}】定时任务总开关已关闭，跳过执行", jobName);
        return true;
    }

    static boolean isWithinExecutionWindow(LocalDateTime now, LocalTime windowStart, LocalTime windowEnd) {
        LocalTime current = now.toLocalTime();
        return !current.isBefore(windowStart) && !current.isAfter(windowEnd);
    }

    private boolean shouldSkipScheduledWindow(LocalDateTime now,
                                              boolean manualTrigger,
                                              LocalTime windowStart,
                                              LocalTime windowEnd,
                                              String jobName) {
        if (manualTrigger) {
            return false;
        }
        if (isWithinExecutionWindow(now, windowStart, windowEnd)) {
            return false;
        }
        log.warn("【{}】当前时间 {} 不在定时执行窗口 {} - {} 内，跳过过期补跑", jobName, now.toLocalTime(), windowStart, windowEnd);
        return true;
    }

    private boolean skipUSPush(String reportName) {
        if (usPushEnabled) {
            return false;
        }
        log.info("【{}】当前已关闭美股推送，跳过执行", reportName);
        return true;
    }

    /**
     * 构建A股无数据时的盘后复盘消息
     */
    private String buildAStockNoDataEveningMessage(String today) {
        return pushLanguageService.text("# 🌆 A股盘后情绪解码 | ", "# 🌆 A-Stock Post-Close Decode | ") + today + "\n\n"
                + pushLanguageService.text("今日 A 股已收盘。系统回溯了日内（9:00-15:00）全网资讯发酵轨迹。\n\n",
                "The A-stock market has closed. The system reviewed the intraday information flow from 09:00 to 15:00.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无 🇨🇳 A股需要解码的异动数据", "⚠️ No A-stock activity met the post-close decoding threshold")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：日内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 same-day mentions for the same ticker; quality over quantity.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 持仓深度体检：\n今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n👉 请在群内直接发送：@",
                "💡 Position Check:\nIf today's tape was confusing, ask whether a hidden risk hit one of your holdings:\n👉 Send @")
                + pushLanguageService.botNameForA()
                + pushLanguageService.text(" 分析 [你的股票代码]\n\n", " analyze [your ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for post-close research only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建A股盘后复盘错误消息
     */
    private String buildAStockErrorEveningMessage(String today, String errorMsg) {
        return pushLanguageService.text("# 🌆 A股盘后情绪解码 | ", "# 🌆 A-Stock Post-Close Decode | ") + today + "\n\n"
                + pushLanguageService.text("今日 A 股已收盘。系统回溯了日内（9:00-15:00）全网资讯发酵轨迹。\n\n",
                "The A-stock market has closed. The system reviewed the intraday information flow from 09:00 to 15:00.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("❌ 数据获取失败: ", "❌ Failed to load data: ") + errorMsg
                + "</font>\n\n"
                + pushLanguageService.text("💡 持仓深度体检：\n今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n👉 请在群内直接发送：@",
                "💡 Position Check:\nIf today's tape was confusing, ask whether a hidden risk hit one of your holdings:\n👉 Send @")
                + pushLanguageService.botNameForA()
                + pushLanguageService.text(" 分析 [你的股票代码]\n\n", " analyze [your ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for post-close research only. It is not investment advice.")
                + "</font>";
    }

    private String buildAPostCloseRiskDigestMessage(String today, AReportFusionContext context) {
        StringBuilder builder = new StringBuilder()
                .append(pushLanguageService.text("# 🌇 A股盘后风险速递 | ", "# 🌇 A-Stock Post-Close Risk Digest | "))
                .append(today).append("\n\n")
                .append(pushLanguageService.text("收盘后公告窗口（15:00-当前）扫描完成，以下为最值得优先处理的新增风险与补充机会。\n\n",
                        "The post-close notice window (15:00 to now) has been scanned. These are the highest-priority new risks and supplemental opportunities.\n\n"));

        if (!context.getRiskAlerts().isEmpty()) {
            builder.append("## ").append(pushLanguageService.text("风险优先级", "Risk Priority")).append("\n");
            context.getRiskAlerts().stream().limit(5).forEach(alert -> appendDigestLine(builder, alert, true));
            builder.append("\n");
        }

        if (!context.getOpportunityAlerts().isEmpty()) {
            builder.append("## ").append(pushLanguageService.text("机会补充", "Opportunity Add-ons")).append("\n");
            context.getOpportunityAlerts().stream().limit(3).forEach(alert -> appendDigestLine(builder, alert, false));
            builder.append("\n");
        }

        builder.append("<font color=\"comment\">")
                .append(pushLanguageService.text("定位：服务收盘后公告密集披露窗口，优先补齐当日 15:30 之后才出现的硬风险和强催化。",
                        "Purpose: cover the dense post-close notice window and catch hard risks or strong catalysts that only appeared after 15:30."))
                .append("</font>");
        return builder.toString();
    }

    private void appendDigestLine(StringBuilder builder, StockAlertDTO<AStockRss> alert, boolean risk) {
        if (alert == null || alert.getStock() == null) {
            return;
        }
        AStockRss stock = alert.getStock();
        builder.append("> **")
                .append(stock.getStockName()).append("(").append(stock.getStockCode()).append(")")
                .append("** | <font color=\"").append(risk ? "warning" : "info").append("\">")
                .append(pushLanguageService.eventType(stock.getEventType())).append("</font>")
                .append(" | ").append(pushLanguageService.text("事件评分 ", "Event Score ")).append(alert.getSignalScore())
                .append(" | ").append(pushLanguageService.text("公告数 ", "Notice Count ")).append(alert.getFrequency()).append("\n");
        builder.append("> ");
        if (pushLanguageService.isEnglish()) {
            builder.append(pushLanguageService.postCloseDigestSummary(
                    stock.getEventType(),
                    alert.getSignalSide(),
                    alert.getSignalScore(),
                    alert.getFrequency()
            ));
        } else {
            builder.append(stock.getTitle());
        }
        builder.append("\n");
    }

    private String buildAPostCloseNoDataMessage(String today) {
        return pushLanguageService.text("# 🌇 A股盘后风险速递 | ", "# 🌇 A-Stock Post-Close Risk Digest | ") + today + "\n\n"
                + pushLanguageService.text("收盘后公告窗口（15:00-当前）扫描完成。\n\n",
                "The post-close notice window (15:00 to now) has been scanned.\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("暂未识别到新增的高优先级风险或强催化公告，保持常规观察即可。",
                "No new high-priority risk or strong catalyst notice has been identified. Routine monitoring is enough for now.")
                + "</font>";
    }

    private String buildAPostCloseErrorMessage(String today, String errorMsg) {
        return pushLanguageService.text("# 🌇 A股盘后风险速递 | ", "# 🌇 A-Stock Post-Close Risk Digest | ") + today + "\n\n"
                + "<font color=\"warning\">" + pushLanguageService.text("❌ 收盘后公告扫描失败：", "❌ Post-close notice scan failed: ")
                + errorMsg
                + "</font>\n\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("请检查公告抓取链路或稍后手动重试。", "Please inspect the notice ingestion pipeline or retry manually later.")
                + "</font>";
    }

    /**
     * 构建美股夜报（盘前预警）无数据消息
     */
    private String buildUSStockNoDataEveningMessage(String today) {
        return pushLanguageService.text("# 🌃 美股夜报 | ", "# 🌃 US Night Radar | ") + today + "\n\n"
                + pushLanguageService.text("系统扫描全网财经资讯源，为今夜美股开盘做准备。\n\n",
                "The system scanned public financial sources to prepare for tonight's US market open.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无 🇺🇸 美股异动预警", "⚠️ No US stock alert met the threshold for tonight")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 mentions for the same ticker in 24h; quality over quantity.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 AI 深度查股：\n想看具体股票分析？请在群内直接发送：@",
                "💡 AI Deep Dive:\nTo analyze a specific stock, send @")
                + pushLanguageService.botNameForUS()
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建美股夜报（盘前预警）错误消息
     */
    private String buildUSStockErrorEveningMessage(String today, String errorMsg) {
        return pushLanguageService.text("# 🌃 美股夜报 | ", "# 🌃 US Night Radar | ") + today + "\n\n"
                + pushLanguageService.text("系统扫描全网财经资讯源。\n\n", "The system scanned public financial information sources.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("❌ 数据获取失败: ", "❌ Failed to load data: ") + errorMsg
                + "</font>\n\n"
                + pushLanguageService.text("💡 AI 深度查股：\n想看具体股票分析？请在群内直接发送：@",
                "💡 AI Deep Dive:\nTo analyze a specific stock, send @")
                + pushLanguageService.botNameForUS()
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建美股早报（隔夜复盘）无数据消息
     */
    private String buildUSOvernightNoDataMessage(String today) {
        return pushLanguageService.text("# 🌅 美股早报 | ", "# 🌅 US Overnight Recap | ") + today + "\n\n"
                + pushLanguageService.text("昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹。\n\n",
                "The US market has closed. The system replayed the full overnight information flow from 21:30 to 04:00 Beijing time.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无 🇺🇸 美股需要解码的隔夜异动数据", "⚠️ No overnight US stock activity met the recap threshold")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 mentions for the same ticker in 24h; quality over quantity.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 隔夜行情复盘：\n昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n👉 请在群内直接发送：@",
                "💡 Overnight Replay:\nIf last night's move caught you off guard, ask what actually hit your watchlist:\n👉 Send @")
                + pushLanguageService.botNameForUS()
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for overnight research only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建美股早报（隔夜复盘）错误消息
     */
    private String buildUSOvernightErrorMessage(String today, String errorMsg) {
        return pushLanguageService.text("# 🌅 美股早报 | ", "# 🌅 US Overnight Recap | ") + today + "\n\n"
                + pushLanguageService.text("昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹。\n\n",
                "The US market has closed. The system replayed the full overnight information flow from 21:30 to 04:00 Beijing time.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("❌ 数据获取失败: ", "❌ Failed to load data: ") + errorMsg
                + "</font>\n\n"
                + pushLanguageService.text("💡 隔夜行情复盘：\n昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n👉 请在群内直接发送：@",
                "💡 Overnight Replay:\nIf last night's move caught you off guard, ask what actually hit your watchlist:\n👉 Send @")
                + pushLanguageService.botNameForUS()
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for overnight research only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建无数据时的消息
     */
    private String buildNoDataMessage(String market, String today) {
        boolean usMarket = market.equals("美股");
        String flag = usMarket ? "🇺🇸" : "🇨🇳";
        String botName = "@" + (usMarket ? pushLanguageService.botNameForUS() : pushLanguageService.botNameForA());
        String title = usMarket
                ? pushLanguageService.text("# 🌅 AI 盘前异动雷达 | ", "# 🌅 AI Pre-Market Alert Radar | ")
                : pushLanguageService.text("# 🌅 A股盘前异动雷达 | ", "# 🌅 A-Stock Pre-Market Alert Radar | ");
        return title + today + "\n\n"
                + pushLanguageService.text("过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音）。\n\n",
                "In the last 24 hours, the system scanned public financial information sources after filtering administrative noise.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无" + flag + " " + market + "异动数据",
                "⚠️ No notable " + (usMarket ? "US stock" : "A-stock") + " alerts were detected")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 mentions for the same ticker in 24h; quality over quantity.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 AI 深度查股：\n想看具体股票分析？请在群内直接发送：",
                "💡 AI Deep Dive:\nTo analyze a specific stock, send: ")
                + botName
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 构建错误消息
     */
    private String buildErrorMessage(String market, String today, String errorMsg) {
        boolean usMarket = market.equals("美股");
        String botName = "@" + (usMarket ? pushLanguageService.botNameForUS() : pushLanguageService.botNameForA());
        String title = usMarket
                ? pushLanguageService.text("# 🌅 AI 盘前异动雷达 | ", "# 🌅 AI Pre-Market Alert Radar | ")
                : pushLanguageService.text("# 🌅 A股盘前异动雷达 | ", "# 🌅 A-Stock Pre-Market Alert Radar | ");
        return title + today + "\n\n"
                + pushLanguageService.text("过去 24 小时内，系统共扫描全网财经资讯源。\n\n",
                "In the last 24 hours, the system scanned public financial information sources.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("❌ 数据获取失败: ", "❌ Failed to load data: ") + errorMsg
                + "</font>\n\n"
                + pushLanguageService.text("💡 AI 深度查股：\n想看具体股票分析？请在群内直接发送：",
                "💡 AI Deep Dive:\nTo analyze a specific stock, send: ")
                + botName
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.")
                + "</font>";
    }
}
