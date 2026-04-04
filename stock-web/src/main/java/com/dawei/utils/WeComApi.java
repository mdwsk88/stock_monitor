package com.dawei.utils;

import com.dawei.entity.AStockMsg;
import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.entity.USStockMsg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * @ClassName WeComApi
 * @Author dawei
 * @Version 1.0
 * @Description 企业微信机器人 Webhook API - 支持多市场推送（美股、A股、港股）
 **/
@Slf4j
@Service
public class WeComApi {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int WECOM_MARKDOWN_MAX_BYTES = 4096;
    private static final int WECOM_MARKDOWN_SAFE_BYTES = 4000;
    private static final int WECOM_MARKDOWN_SECTION_SPLIT_TARGET_BYTES = 3000;
    private static final int WECOM_MARKDOWN_LINE_MAX_BYTES = 320;
    private static final List<String> COMMON_MARKDOWN_EMOJIS = List.of(
            "🌅", "🌆", "🧭", "🎯", "🧠", "🔗", "📈", "⚠️", "⚠", "🔥", "💡", "👉", "📌",
            "🏢", "📅", "📰", "🏷️", "🏷", "📊", "📎", "🗂️", "🗂", "🇨🇳", "️"
    );

    // 美股 Webhook URL
    @Value("${WECOM_WEBHOOK_URL_US:}")
    private String webhookUrlUs;

    // A股 Webhook URL
    @Value("${WECOM_WEBHOOK_URL_A:}")
    private String webhookUrlA;

    // 港股 Webhook URL
    @Value("${WECOM_WEBHOOK_URL_HK:}")
    private String webhookUrlHk;

    // 兼容旧配置（如果新的配置不存在，则使用旧的配置）
    @Value("${WECOM_WEBHOOK_URL:}")
    private String webhookUrlDefault;

    private final RestTemplate restTemplate;
    private final PushLanguageService pushLanguageService;
    private final Executor notificationExecutor;

    public WeComApi(RestTemplate restTemplate) {
        this(restTemplate, new PushLanguageService(), Runnable::run);
    }

    public WeComApi(RestTemplate restTemplate, PushLanguageService pushLanguageService) {
        this(restTemplate, pushLanguageService, Runnable::run);
    }

    @Autowired
    public WeComApi(RestTemplate restTemplate,
                    PushLanguageService pushLanguageService,
                    @Qualifier("notificationExecutor") Executor notificationExecutor) {
        this.restTemplate = restTemplate;
        this.pushLanguageService = pushLanguageService;
        this.notificationExecutor = notificationExecutor;
    }

    /**
     * 市场类型枚举
     */
    public enum MarketType {
        US,   // 美股
        A,    // A股
        HK    // 港股
    }

    /**
     * 获取指定市场的 Webhook URL
     */
    private String getWebhookUrl(MarketType marketType) {
        String url = switch (marketType) {
            case US -> webhookUrlUs;
            case A -> webhookUrlA;
            case HK -> webhookUrlHk;
        };
        
        // 如果特定市场的配置为空，则尝试使用默认配置
        if (url == null || url.isEmpty()) {
            url = webhookUrlDefault;
        }
        
        return url;
    }

    /**
     * 获取指定市场的机器人名称
     */
    private String getBotName(MarketType marketType) {
        return switch (marketType) {
            case US -> pushLanguageService.botNameForUS();
            case A -> pushLanguageService.botNameForA();
            case HK -> pushLanguageService.botNameForHK();
        };
    }

    /**
     * @Description: 格式化单条股票消息
     * @Author dawei
     * @param stockMsg
     */
    public String formatStockInfo(USStockMsg stockMsg) {
        return "<font color=\"info\">📌 " + pushLanguageService.text("代码", "Ticker") + "：</font>" + stockMsg.getStockCode() + "\n" +
               "<font color=\"comment\">📅 " + pushLanguageService.text("时间", "Time") + "：</font>" + stockMsg.getPubDateBj() + "\n" +
               "<font color=\"comment\">📰 " + pushLanguageService.text("标题（英文）", "Headline (EN)") + "：</font>" + stockMsg.getTitle() + "\n" +
               "<font color=\"comment\">📰 " + pushLanguageService.text("标题（中文）", "Headline (ZH)") + "：</font>" + stockMsg.getTitleZh() + "\n" +
               "<font color=\"warning\">🏷️ " + pushLanguageService.text("标签", "Tags") + "：</font>" + stockMsg.getTags() + "\n" +
               "<font color=\"comment\">📊 " + pushLanguageService.text("统计", "Stats") + "：</font>"
               + pushLanguageService.text("24小时异动=", "24h mentions=") + stockMsg.getCounts24Hour() + pushLanguageService.text("次; ", "; ")
               + pushLanguageService.text("3天内异动=", "3d mentions=") + stockMsg.getCounts3Day() + pushLanguageService.text("次; ", "; ")
               + pushLanguageService.text("1周内异动=", "1w mentions=") + stockMsg.getCounts1Week() + pushLanguageService.text("次", "");
    }

    /**
     * @Description: 格式化多条股票消息
     * @Author dawei
     * @param stocks
     */
    public String formatStockInfoFromList(List<USStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return pushLanguageService.text("暂无股票异动信息", "No stock alerts available");
        }
        return stocks.stream()
                .map(this::formatStockInfo)
                .collect(Collectors.joining("\n\n----------\n\n"));
    }

    /**
     * @Description: 格式化单条股票消息为普通文本格式（用于文本消息）
     * @Author dawei
     * @param stockMsg
     */
    public String formatStockInfoText(USStockMsg stockMsg) {
        return "📌 " + pushLanguageService.text("代码", "Ticker") + "：" + stockMsg.getStockCode() + "\n" +
               "📅 " + pushLanguageService.text("时间", "Time") + "：" + stockMsg.getPubDateBj() + "\n" +
               "📰 " + pushLanguageService.text("标题（英文）", "Headline (EN)") + "：" + stockMsg.getTitle() + "\n" +
               "📰 " + pushLanguageService.text("标题（中文）", "Headline (ZH)") + "：" + stockMsg.getTitleZh() + "\n" +
               "🏷️ " + pushLanguageService.text("标签", "Tags") + "：" + stockMsg.getTags() + "\n" +
               "📊 " + pushLanguageService.text("统计", "Stats") + "："
               + pushLanguageService.text("24小时异动=", "24h mentions=") + stockMsg.getCounts24Hour() + pushLanguageService.text("次; ", "; ")
               + pushLanguageService.text("3天内异动=", "3d mentions=") + stockMsg.getCounts3Day() + pushLanguageService.text("次; ", "; ")
               + pushLanguageService.text("1周内异动=", "1w mentions=") + stockMsg.getCounts1Week() + pushLanguageService.text("次", "");
    }

    /**
     * @Description: 格式化多条股票消息为普通文本格式（用于文本消息）
     * @Author dawei
     * @param stocks
     */
    public String formatStockInfoTextFromList(List<USStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return pushLanguageService.text("暂无股票异动信息", "No stock alerts available");
        }
        return stocks.stream()
                .map(this::formatStockInfoText)
                .collect(Collectors.joining("\n\n----------\n\n"));
    }

    /**
     * @Description: 发送 Markdown 格式消息到企业微信（指定市场）
     * @Author dawei
     * @param markdownContent 消息内容
     * @param marketType 市场类型（美股、A股、港股）
     */
    public void sendMarkdownMessage(String markdownContent, MarketType marketType) {
        boolean success = sendMessage(markdownContent, "markdown", marketType);
        if (!success && hasWebhook(marketType)) {
            throw new RuntimeException("企业微信消息发送失败");
        }
    }

    public CompletableFuture<Boolean> sendMarkdownMessageAsync(String markdownContent, MarketType marketType) {
        List<String> preparedContents = prepareContentsForSend(markdownContent, "markdown", marketType);
        return CompletableFuture.supplyAsync(
                () -> sendPreparedContents(preparedContents, "markdown", marketType),
                notificationExecutor
        );
    }

    /**
     * @Description: 发送 Markdown 格式消息到企业微信（向后兼容，使用默认配置）
     * @Author dawei
     * @param markdownContent 消息内容
     */
    public void sendMarkdownMessage(String markdownContent) {
        sendMarkdownMessage(markdownContent, MarketType.US);
    }

    public CompletableFuture<Boolean> sendMarkdownMessageAsync(String markdownContent) {
        return sendMarkdownMessageAsync(markdownContent, MarketType.US);
    }

    /**
     * @Description: 发送文本消息到企业微信（指定市场）
     * @Author dawei
     * @param textContent 消息内容
     * @param marketType 市场类型（美股、A股、港股）
     */
    public void sendTextMessage(String textContent, MarketType marketType) {
        boolean success = sendMessage(textContent, "text", marketType);
        if (!success && hasWebhook(marketType)) {
            throw new RuntimeException("企业微信消息发送失败");
        }
    }

    public CompletableFuture<Boolean> sendTextMessageAsync(String textContent, MarketType marketType) {
        List<String> preparedContents = prepareContentsForSend(textContent, "text", marketType);
        return CompletableFuture.supplyAsync(
                () -> sendPreparedContents(preparedContents, "text", marketType),
                notificationExecutor
        );
    }

    /**
     * @Description: 发送文本消息到企业微信（向后兼容，使用默认配置）
     * @Author dawei
     * @param textContent 消息内容
     */
    public void sendTextMessage(String textContent) {
        sendTextMessage(textContent, MarketType.US);
    }

    public CompletableFuture<Boolean> sendTextMessageAsync(String textContent) {
        return sendTextMessageAsync(textContent, MarketType.US);
    }

    private boolean sendMessage(String content, String messageType, MarketType marketType) {
        return sendPreparedContents(prepareContentsForSend(content, messageType, marketType), messageType, marketType);
    }

    private boolean sendPreparedContents(List<String> finalContents, String messageType, MarketType marketType) {
        String webhookUrl = getWebhookUrl(marketType);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("企业微信 webhook URL 未配置（市场: {}），跳过发送", marketType);
            return false;
        }
        try {
            for (int i = 0; i < finalContents.size(); i++) {
                String finalContent = finalContents.get(i);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> contentBody = new HashMap<>();
                contentBody.put("content", finalContent);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("msgtype", messageType);
                requestBody.put(messageType, contentBody);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                String response = restTemplate.postForObject(webhookUrl, request, String.class);
                int responseErrCode = extractErrCode(response);
                if (responseErrCode == 0) {
                    log.info("企业微信消息发送成功（市场: {}，类型: {}，part={}/{}, chars={}，bytes={}），响应: {}",
                            marketType, messageType, i + 1, finalContents.size(),
                            finalContent.length(), utf8Length(finalContent), response);
                    continue;
                }
                log.error("企业微信消息发送失败（市场: {}，类型: {}，part={}/{}, chars={}，bytes={}，errcode={}），响应: {}",
                        marketType, messageType, i + 1, finalContents.size(),
                        finalContent.length(), utf8Length(finalContent), responseErrCode, response);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("企业微信消息发送失败（市场: {}，类型: {}）: {}", marketType, messageType, e.getMessage(), e);
            return false;
        }
    }

    private boolean hasWebhook(MarketType marketType) {
        String webhookUrl = getWebhookUrl(marketType);
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    // ============== A股相关方法 ==============

    /**
     * @Description: 格式化单条A股公告消息
     * @Author dawei
     * @param aStockMsg
     */
    public String formatAStockInfo(AStockMsg aStockMsg) {
        StringBuilder builder = new StringBuilder()
                .append("<font color=\"info\">📌 ").append(pushLanguageService.text("代码", "Ticker")).append("：</font>").append(aStockMsg.getStockCode()).append("\n")
                .append("<font color=\"info\">🏢 ").append(pushLanguageService.text("名称", "Name")).append("：</font>").append(aStockMsg.getStockName()).append("\n")
                .append("<font color=\"comment\">📅 ").append(pushLanguageService.text("时间", "Time")).append("：</font>").append(aStockMsg.getPubDate()).append("\n")
                .append("<font color=\"comment\">📰 ").append(pushLanguageService.text("标题", "Headline")).append("：</font>").append(aStockMsg.getTitle()).append("\n")
                .append("<font color=\"warning\">🏷️ ").append(pushLanguageService.text("类型", "Type")).append("：</font>").append(aStockMsg.getTag()).append("\n")
                .append("<font color=\"comment\">🧭 ").append(pushLanguageService.text("事件", "Event")).append("：</font>")
                .append(pushLanguageService.eventType(aStockMsg.getEventType()))
                .append(" / ").append(pushLanguageService.signalSideLabel(aStockMsg.getSignalSide())).append("\n")
                .append("<font color=\"comment\">🎯 ").append(pushLanguageService.text("评分", "Score")).append("：</font>")
                .append(aStockMsg.getSignalScore()).append(pushLanguageService.text(" 分\n", "\n"));

        if (aStockMsg.getBatchNoticeCount() != null && aStockMsg.getBatchNoticeCount() > 1) {
            builder.append("<font color=\"comment\">📎 ")
                    .append(pushLanguageService.text("本轮命中", "Matches in this batch"))
                    .append("：</font>")
                    .append(aStockMsg.getBatchNoticeCount())
                    .append(pushLanguageService.text("条\n", "\n"));
        }
        if (aStockMsg.getRelatedTitles() != null && !aStockMsg.getRelatedTitles().isBlank()) {
            builder.append("<font color=\"comment\">🗂️ ")
                    .append(pushLanguageService.text("其他标题", "Other headlines"))
                    .append("：</font>")
                    .append(aStockMsg.getRelatedTitles()).append("\n");
        }

        builder.append("<font color=\"comment\">📊 ")
                .append(pushLanguageService.text("支撑", "Support"))
                .append("：</font>")
                .append(pushLanguageService.text("24小时公告=", "24h notices="))
                .append(aStockMsg.getCounts24Hour()).append(pushLanguageService.text("条; ", "; "))
                .append(pushLanguageService.text("3天内公告=", "3d notices=")).append(aStockMsg.getCounts3Day()).append(pushLanguageService.text("条; ", "; "))
                .append(pushLanguageService.text("1周内公告=", "1w notices=")).append(aStockMsg.getCounts1Week()).append(pushLanguageService.text("条", ""));
        return builder.toString();
    }

    /**
     * @Description: 格式化多条A股公告消息
     * @Author dawei
     * @param stocks
     */
    public String formatAStockInfoFromList(List<AStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return pushLanguageService.text("暂无A股公告信息", "No A-stock notice alerts available");
        }

        return stocks.stream()
                .map(this::formatAStockInfo)
                .collect(Collectors.joining("\n\n----------\n\n"));
    }

    /**
     * 格式化A股盘中实时预警卡片
     */
    public String formatAStockRealtimeAlert(AStockRealtimeAlertCard card) {
        if (card == null) {
            return pushLanguageService.text("暂无A股实时预警", "No A-stock intraday alert");
        }

        boolean riskAlert = card.getPushType() != null && card.getPushType().name().contains("RISK");
        String defaultState = pushLanguageService.text("中性", "Neutral");
        String severityLabel = StringUtils.defaultIfBlank(card.getSeverityLabel(), riskAlert
                ? pushLanguageService.text("高优先级风险", "High-Priority Risk")
                : pushLanguageService.text("高优先级机会", "High-Priority Opportunity"));
        String eventType = pushLanguageService.eventType(card.getEventType());
        String positionLabel = pushLanguageService.positionLabel(card.getPositionLabel());
        String positionReason = pushLanguageService.translatePositionReason(card.getPositionReason());
        String relationReason = pushLanguageService.translateRelationReason(card.getRelationReason());
        String titleLineLabel = pushLanguageService.text("核心公告", "Core Notice");
        String tradeActionLabel = pushLanguageService.text("交易动作", "Trade Action");

        StringBuilder builder = new StringBuilder()
                .append("# ").append(card.getEmoji()).append(" ")
                .append(pushLanguageService.text(
                        "A股盘中" + (riskAlert ? "风险预警" : "突发预警"),
                        riskAlert ? "A-Stock Intraday Risk Alert" : "A-Stock Intraday Breakout Alert"
                ))
                .append("\n\n")
                .append("> **").append(pushLanguageService.text("标的", "Ticker")).append("**：")
                .append(card.getStockName()).append("(").append(card.getStockCode()).append(")\n")
                .append("> **").append(pushLanguageService.text("市场状态", "Market Regime")).append("**：")
                .append(card.getMarketStateLabel() != null ? card.getMarketStateLabel() : defaultState).append("\n")
                .append("> **").append(pushLanguageService.text("定性", "Severity")).append("**：<font color=\"")
                .append(riskAlert ? "warning" : "info")
                .append("\">").append(severityLabel).append("</font>")
                .append(" | ").append(eventType).append(" | ").append(card.getSignalScore())
                .append(pushLanguageService.text(" 分\n", "\n"))
                .append("> **").append(titleLineLabel).append("**：").append(card.getTitle()).append("\n")
                .append("> **").append(pushLanguageService.text("结论", "Conclusion")).append("**：").append(card.getConclusion()).append("\n")
                .append("> **").append(pushLanguageService.text("推演", "Reasoning")).append("**：").append(card.getReasoning()).append("\n");

        if (!riskAlert && card.getPositionLabel() != null && !card.getPositionLabel().isBlank()) {
            builder.append("> **").append(pushLanguageService.text("身位判定", "Positioning")).append("**：<font color=\"")
                    .append(card.getPositionColorTag())
                    .append("\">").append(positionLabel).append("</font>");
            if (StringUtils.isNotBlank(positionReason)) {
                builder.append(" | ").append(positionReason);
            }
            builder.append("\n");
        }

        if (card.getMacroThemeName() != null && !card.getMacroThemeName().isBlank()) {
            builder.append("> **").append(pushLanguageService.text("主线共振", "Theme Resonance")).append("**：【")
                    .append(card.getMacroThemeName()).append("】");
            if (card.getMacroSignalScore() != null && card.getMacroSignalScore() > 0) {
                builder.append(pushLanguageService.text("（主题分 ", " (Theme Score "))
                        .append(card.getMacroSignalScore()).append(pushLanguageService.text("）", ")"));
            }
            if (card.getResonanceScore() != null && card.getResonanceScore() > 0) {
                builder.append(pushLanguageService.text("，融合分 ", ", Fusion Score "))
                        .append(card.getResonanceScore());
            }
            builder.append("\n");
            if (StringUtils.isNotBlank(relationReason)) {
                builder.append("> **").append(pushLanguageService.text("共振依据", "Why It Resonates")).append("**：")
                        .append(relationReason).append("\n");
            }
            if (card.getMacroTitle() != null && !card.getMacroTitle().isBlank()) {
                builder.append("> **").append(pushLanguageService.text("主题快讯", "Macro Trigger")).append("**：")
                        .append(card.getMacroTitle()).append("\n");
            }
        }

        if (!riskAlert && card.getTradeHint() != null && !card.getTradeHint().isBlank()) {
            builder.append("> **").append(tradeActionLabel).append("**：")
                    .append(pushLanguageService.translateTradeHint(card.getTradeHint())).append("\n");
        }

        builder.append("> **").append(pushLanguageService.text("提醒", "Reminder")).append("**：")
                .append(card.getRiskHint()).append("\n\n")
                .append("<font color=\"comment\">")
                .append(pushLanguageService.text("仅供盘中研究与信息跟踪，不构成任何投资建议。", "For intraday research and information tracking only. Not investment advice."))
                .append("</font>");
        return builder.toString();
    }

    private List<String> prepareContentsForSend(String content, String messageType, MarketType marketType) {
        String safeContent = content == null ? "" : content;
        if (!"markdown".equalsIgnoreCase(messageType)) {
            return List.of(safeContent);
        }
        int originalBytes = utf8Length(safeContent);
        List<String> preparedParts = splitMarkdownForWeCom(safeContent).stream()
                .map(this::shrinkMarkdownForWeCom)
                .toList();
        if (preparedParts.size() > 1) {
            log.warn("企业微信 Markdown 已分段发送（市场: {}），parts={}，originalChars={}，originalBytes={}",
                    marketType, preparedParts.size(), safeContent.length(), originalBytes);
        }
        int finalBytes = preparedParts.stream().mapToInt(this::utf8Length).sum();
        int finalChars = preparedParts.stream().mapToInt(String::length).sum();
        if (originalBytes > WECOM_MARKDOWN_MAX_BYTES
                || preparedParts.size() > 1
                || preparedParts.stream().noneMatch(safeContent::equals)) {
            log.warn("企业微信 Markdown 已压缩（市场: {}），chars {} -> {}，bytes {} -> {}",
                    marketType, safeContent.length(), finalChars, originalBytes, finalBytes);
        }
        return preparedParts;
    }

    private String shrinkMarkdownForWeCom(String content) {
        String normalized = normalizeMarkdown(content);
        if (utf8Length(normalized) <= WECOM_MARKDOWN_MAX_BYTES) {
            return normalized;
        }

        String compacted = collapseBlankLines(normalized);
        compacted = stripFontTags(compacted);
        compacted = stripCommonEmojis(compacted);
        compacted = stripTrailingInteractiveBlock(compacted);
        compacted = compactVisualSpacing(compacted);
        if (utf8Length(compacted) <= WECOM_MARKDOWN_MAX_BYTES) {
            return compacted;
        }

        return truncateMarkdownByBytes(compacted, WECOM_MARKDOWN_SAFE_BYTES);
    }

    private List<String> splitMarkdownForWeCom(String content) {
        String normalized = normalizeMarkdown(content);
        if (utf8Length(normalized) <= WECOM_MARKDOWN_MAX_BYTES) {
            return List.of(normalized);
        }

        MarkdownDocument document = parseMarkdownDocument(normalized);
        if (document.sections().size() < 2) {
            return List.of(normalized);
        }

        List<List<MarkdownSection>> groups = new ArrayList<>();
        List<MarkdownSection> currentGroup = new ArrayList<>();
        int currentBytes = utf8Length(renderMarkdownPart(document, List.of(), 1, 1));
        for (MarkdownSection section : document.sections()) {
            int sectionBytes = utf8Length(renderSection(section));
            if (!currentGroup.isEmpty()
                    && currentBytes + sectionBytes > WECOM_MARKDOWN_SECTION_SPLIT_TARGET_BYTES) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                currentBytes = utf8Length(renderMarkdownPart(document, List.of(), groups.size() + 1, groups.size() + 1));
            }
            currentGroup.add(section);
            currentBytes += sectionBytes;
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        if (groups.size() <= 1) {
            return List.of(normalized);
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            parts.add(renderMarkdownPart(document, groups.get(i), i + 1, groups.size()));
        }
        return parts;
    }

    private String normalizeMarkdown(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String collapseBlankLines(String content) {
        return content.replaceAll("\n{3,}", "\n\n");
    }

    private String stripFontTags(String content) {
        return content
                .replaceAll("<font color=\"[^\"]+\">", "")
                .replace("</font>", "");
    }

    private String stripCommonEmojis(String content) {
        String stripped = content;
        for (String emoji : COMMON_MARKDOWN_EMOJIS) {
            stripped = stripped.replace(emoji, "");
        }
        return stripped;
    }

    private String stripTrailingInteractiveBlock(String content) {
        List<String> keptLines = new ArrayList<>();
        boolean dropTail = false;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("💡")
                    || trimmed.startsWith("Deep Dive")
                    || trimmed.startsWith("Position Check")
                    || trimmed.startsWith("Tip:")
                    || trimmed.startsWith("AI 深度查股")
                    || trimmed.startsWith("持仓深度体检")
                    || trimmed.startsWith("👉")
                    || trimmed.contains("免责声明")
                    || trimmed.toLowerCase().contains("disclaimer")) {
                dropTail = true;
            }
            if (!dropTail) {
                keptLines.add(line);
            }
        }
        return trimTrailingBlankLines(String.join("\n", keptLines));
    }

    private String compactVisualSpacing(String content) {
        return trimTrailingBlankLines(content
                .replaceAll("(?m)^>\\s{2,}", "> ")
                .replaceAll("(?m)^\\s{2,}", "")
                .replaceAll("[ \t]{2,}", " "));
    }

    private String truncateMarkdownByBytes(String content, int maxBytes) {
        String notice = pushLanguageService.text("\n\n[内容过长，已截断]", "\n\n[Content truncated]");
        int noticeBytes = utf8Length(notice);
        int budget = Math.max(0, maxBytes - noticeBytes);

        List<String> lines = List.of(content.split("\n", -1));
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        for (String rawLine : lines) {
            String line = truncateLineByBytes(rawLine, WECOM_MARKDOWN_LINE_MAX_BYTES);
            String candidate = builder.isEmpty() ? line : builder + "\n" + line;
            if (utf8Length(candidate) > budget) {
                truncated = true;
                break;
            }
            builder.setLength(0);
            builder.append(candidate);
        }

        String result = trimTrailingBlankLines(builder.toString());
        if (!truncated && utf8Length(result) <= WECOM_MARKDOWN_MAX_BYTES) {
            return result;
        }
        result = appendTruncationNotice(result, notice, maxBytes);
        if (utf8Length(result) <= WECOM_MARKDOWN_MAX_BYTES) {
            return result;
        }
        return trimToBytes(result, WECOM_MARKDOWN_MAX_BYTES);
    }

    private String truncateLineByBytes(String line, int maxBytes) {
        if (utf8Length(line) <= maxBytes) {
            return line;
        }
        return trimToBytes(line, maxBytes - utf8Length("...")) + "...";
    }

    private String appendTruncationNotice(String content, String notice, int maxBytes) {
        String base = trimTrailingBlankLines(content);
        while (!base.isEmpty() && utf8Length(base + notice) > maxBytes) {
            int cut = base.lastIndexOf('\n');
            if (cut < 0) {
                base = trimToBytes(base, Math.max(0, maxBytes - utf8Length(notice)));
                break;
            }
            base = trimTrailingBlankLines(base.substring(0, cut));
        }
        return trimTrailingBlankLines(base) + notice;
    }

    private String trimToBytes(String text, int maxBytes) {
        if (maxBytes <= 0 || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String asString = new String(Character.toChars(codePoint));
            int cpBytes = utf8Length(asString);
            if (bytes + cpBytes > maxBytes) {
                break;
            }
            builder.append(asString);
            bytes += cpBytes;
            i += Character.charCount(codePoint);
        }
        return trimTrailingBlankLines(builder.toString());
    }

    private String trimTrailingBlankLines(String content) {
        return content.replaceAll("(\\n\\s*)+$", "").trim();
    }

    private MarkdownDocument parseMarkdownDocument(String content) {
        List<String> lines = List.of(content.split("\n", -1));
        String title = "";
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (!line.isEmpty()) {
                title = line;
                index++;
                break;
            }
            index++;
        }

        List<String> introLines = new ArrayList<>();
        List<MarkdownSection> sections = new ArrayList<>();
        String currentHeading = null;
        List<String> currentBody = new ArrayList<>();

        for (; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("## ")) {
                if (currentHeading != null) {
                    sections.add(new MarkdownSection(currentHeading, trimLines(currentBody)));
                    currentBody = new ArrayList<>();
                }
                currentHeading = line.trim();
                continue;
            }
            if (currentHeading == null) {
                introLines.add(line);
            } else {
                currentBody.add(line);
            }
        }
        if (currentHeading != null) {
            sections.add(new MarkdownSection(currentHeading, trimLines(currentBody)));
        }
        return new MarkdownDocument(title, trimLines(introLines), sections);
    }

    private List<String> trimLines(List<String> lines) {
        List<String> result = new ArrayList<>(lines);
        while (!result.isEmpty() && result.get(0).trim().isEmpty()) {
            result.remove(0);
        }
        while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private String renderMarkdownPart(MarkdownDocument document,
                                      List<MarkdownSection> sections,
                                      int partIndex,
                                      int totalParts) {
        StringBuilder builder = new StringBuilder();
        if (!document.title().isBlank()) {
            builder.append(appendPartSuffix(document.title(), partIndex, totalParts)).append("\n\n");
        }
        if (partIndex == 1 && !document.introLines().isEmpty()) {
            builder.append(String.join("\n", document.introLines())).append("\n\n");
        } else if (partIndex > 1) {
            builder.append(pushLanguageService.text("以下为续篇：", "Continued below:")).append("\n\n");
        }
        for (MarkdownSection section : sections) {
            builder.append(renderSection(section)).append("\n\n");
        }
        return trimTrailingBlankLines(builder.toString());
    }

    private String renderSection(MarkdownSection section) {
        StringBuilder builder = new StringBuilder();
        builder.append(section.heading()).append("\n\n");
        if (!section.bodyLines().isEmpty()) {
            builder.append(String.join("\n", section.bodyLines()));
        }
        return trimTrailingBlankLines(builder.toString());
    }

    private String appendPartSuffix(String title, int partIndex, int totalParts) {
        if (totalParts <= 1 || title.isBlank()) {
            return title;
        }
        return title + "（" + partIndex + "/" + totalParts + "）";
    }

    private int extractErrCode(String response) {
        if (response == null || response.isBlank()) {
            return -1;
        }
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(response);
            return jsonNode.path("errcode").asInt(-1);
        } catch (Exception e) {
            log.warn("企业微信响应解析失败，按发送失败处理。response={}", response);
            return -1;
        }
    }

    private int utf8Length(String content) {
        return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
    }

    private record MarkdownDocument(String title,
                                    List<String> introLines,
                                    List<MarkdownSection> sections) {
    }

    private record MarkdownSection(String heading,
                                   List<String> bodyLines) {
    }
}
