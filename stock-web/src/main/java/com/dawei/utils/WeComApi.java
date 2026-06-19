package com.dawei.utils;

import com.dawei.entity.AStockMsg;
import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.entity.USStockMsg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @ClassName WeComApi
 * @Author dawei
 * @Version 1.0
 * @Description 企业微信机器人 Webhook API - 支持多市场推送（美股、A股、港股）
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComApi {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int WECOM_MARKDOWN_MAX_BYTES = 4096;
    private static final int WECOM_MARKDOWN_SAFE_BYTES = 4000;
    private static final int WECOM_MARKDOWN_SECTION_SPLIT_TARGET_BYTES = 3000;
    private static final int WECOM_MARKDOWN_LINE_MAX_BYTES = 320;
    private static final String MARKDOWN_TRUNCATION_NOTICE = "\n\n[内容过长，已截断]";
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
            case US -> "美股分析专家";
            case A -> "A股分析专家";
            case HK -> "港股分析专家";
        };
    }

    /**
     * @Description: 格式化单条股票消息
     * @Author dawei
     * @param stockMsg
     */
    public String formatStockInfo(USStockMsg stockMsg) {
        return "<font color=\"info\">📌 代码：</font>" + stockMsg.getStockCode() + "\n" +
               "<font color=\"comment\">📅 时间：</font>" + stockMsg.getPubDateBj() + "\n" +
               "<font color=\"comment\">📰 标题（英文）：</font>" + stockMsg.getTitle() + "\n" +
               "<font color=\"comment\">📰 标题（中文）：</font>" + stockMsg.getTitleZh() + "\n" +
               "<font color=\"warning\">🏷️ 标签：</font>" + stockMsg.getTags() + "\n" +
               "<font color=\"comment\">📊 统计：</font>24小时异动=" + stockMsg.getCounts24Hour() + "次; " +
               "3天内异动=" + stockMsg.getCounts3Day() + "次; " +
               "1周内异动=" + stockMsg.getCounts1Week() + "次";
    }

    /**
     * @Description: 格式化多条股票消息
     * @Author dawei
     * @param stocks
     */
    public String formatStockInfoFromList(List<USStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return "暂无股票异动信息";
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
        return "📌 代码：" + stockMsg.getStockCode() + "\n" +
               "📅 时间：" + stockMsg.getPubDateBj() + "\n" +
               "📰 标题（英文）：" + stockMsg.getTitle() + "\n" +
               "📰 标题（中文）：" + stockMsg.getTitleZh() + "\n" +
               "🏷️ 标签：" + stockMsg.getTags() + "\n" +
               "📊 统计：24小时异动=" + stockMsg.getCounts24Hour() + "次; " +
               "3天内异动=" + stockMsg.getCounts3Day() + "次; " +
               "1周内异动=" + stockMsg.getCounts1Week() + "次";
    }

    /**
     * @Description: 格式化多条股票消息为普通文本格式（用于文本消息）
     * @Author dawei
     * @param stocks
     */
    public String formatStockInfoTextFromList(List<USStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return "暂无股票异动信息";
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

    @Async("notificationExecutor")
    public CompletableFuture<Boolean> sendMarkdownMessageAsync(String markdownContent, MarketType marketType) {
        return CompletableFuture.completedFuture(sendMessage(markdownContent, "markdown", marketType));
    }

    /**
     * @Description: 发送 Markdown 格式消息到企业微信（向后兼容，使用默认配置）
     * @Author dawei
     * @param markdownContent 消息内容
     */
    public void sendMarkdownMessage(String markdownContent) {
        sendMarkdownMessage(markdownContent, MarketType.US);
    }

    @Async("notificationExecutor")
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

    @Async("notificationExecutor")
    public CompletableFuture<Boolean> sendTextMessageAsync(String textContent, MarketType marketType) {
        return CompletableFuture.completedFuture(sendMessage(textContent, "text", marketType));
    }

    /**
     * @Description: 发送文本消息到企业微信（向后兼容，使用默认配置）
     * @Author dawei
     * @param textContent 消息内容
     */
    public void sendTextMessage(String textContent) {
        sendTextMessage(textContent, MarketType.US);
    }

    @Async("notificationExecutor")
    public CompletableFuture<Boolean> sendTextMessageAsync(String textContent) {
        return sendTextMessageAsync(textContent, MarketType.US);
    }

    private boolean sendMessage(String content, String messageType, MarketType marketType) {
        String webhookUrl = getWebhookUrl(marketType);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("企业微信 webhook URL 未配置（市场: {}），跳过发送", marketType);
            return false;
        }

        try {
            List<String> finalContents = prepareContentsForSend(content, messageType, marketType);
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
                .append("<font color=\"info\">📌 代码：</font>").append(aStockMsg.getStockCode()).append("\n")
                .append("<font color=\"info\">🏢 名称：</font>").append(aStockMsg.getStockName()).append("\n")
                .append("<font color=\"comment\">📅 时间：</font>").append(aStockMsg.getPubDate()).append("\n")
                .append("<font color=\"comment\">📰 标题：</font>").append(aStockMsg.getTitle()).append("\n")
                .append("<font color=\"warning\">🏷️ 类型：</font>").append(aStockMsg.getTag()).append("\n")
                .append("<font color=\"comment\">🧭 事件：</font>").append(aStockMsg.getEventType())
                .append(" / ").append(aStockMsg.getSignalSide()).append("\n")
                .append("<font color=\"comment\">🎯 评分：</font>").append(aStockMsg.getSignalScore()).append(" 分\n");

        if (aStockMsg.getBatchNoticeCount() != null && aStockMsg.getBatchNoticeCount() > 1) {
            builder.append("<font color=\"comment\">📎 本轮命中：</font>")
                    .append(aStockMsg.getBatchNoticeCount()).append("条\n");
        }
        if (aStockMsg.getRelatedTitles() != null && !aStockMsg.getRelatedTitles().isBlank()) {
            builder.append("<font color=\"comment\">🗂️ 其他标题：</font>")
                    .append(aStockMsg.getRelatedTitles()).append("\n");
        }

        builder.append("<font color=\"comment\">📊 支撑：</font>24小时公告=")
                .append(aStockMsg.getCounts24Hour()).append("条; ")
                .append("3天内公告=").append(aStockMsg.getCounts3Day()).append("条; ")
                .append("1周内公告=").append(aStockMsg.getCounts1Week()).append("条");
        return builder.toString();
    }

    /**
     * @Description: 格式化多条A股公告消息
     * @Author dawei
     * @param stocks
     */
    public String formatAStockInfoFromList(List<AStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return "暂无A股公告信息";
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
            return "暂无A股实时预警";
        }

        boolean riskAlert = card.getPushType() != null && card.getPushType().name().contains("RISK");

        StringBuilder builder = new StringBuilder()
                .append("# ").append(card.getEmoji()).append(" A股盘中")
                .append(riskAlert ? "风险预警" : "突发预警")
                .append("\n\n")
                .append("> **标的**：").append(card.getStockName()).append("(").append(card.getStockCode()).append(")\n")
                .append("> **市场状态**：").append(card.getMarketStateLabel() != null ? card.getMarketStateLabel() : "中性").append("\n")
                .append("> **定性**：<font color=\"")
                .append(riskAlert ? "warning" : "info")
                .append("\">").append(card.getSeverityLabel()).append("</font>")
                .append(" | ").append(card.getEventType()).append(" | ").append(card.getSignalScore()).append(" 分\n")
                .append("> **核心公告**：").append(card.getTitle()).append("\n")
                .append("> **结论**：").append(card.getConclusion()).append("\n")
                .append("> **推演**：").append(card.getReasoning()).append("\n");

        if (!riskAlert && card.getPositionLabel() != null && !card.getPositionLabel().isBlank()) {
            builder.append("> **身位判定**：<font color=\"")
                    .append(card.getPositionColorTag())
                    .append("\">").append(card.getPositionLabel()).append("</font>");
            if (card.getPositionReason() != null && !card.getPositionReason().isBlank()) {
                builder.append(" | ").append(card.getPositionReason());
            }
            builder.append("\n");
        }

        if (card.getMacroThemeName() != null && !card.getMacroThemeName().isBlank()) {
            builder.append("> **主线共振**：【").append(card.getMacroThemeName()).append("】");
            if (card.getMacroSignalScore() != null && card.getMacroSignalScore() > 0) {
                builder.append("（主题分 ").append(card.getMacroSignalScore()).append("）");
            }
            if (card.getResonanceScore() != null && card.getResonanceScore() > 0) {
                builder.append("，融合分 ").append(card.getResonanceScore());
            }
            builder.append("\n");
            if (card.getRelationReason() != null && !card.getRelationReason().isBlank()) {
                builder.append("> **共振依据**：").append(card.getRelationReason()).append("\n");
            }
            if (card.getMacroTitle() != null && !card.getMacroTitle().isBlank()) {
                builder.append("> **主题快讯**：").append(card.getMacroTitle()).append("\n");
            }
        }

        if (!riskAlert && card.getTradeHint() != null && !card.getTradeHint().isBlank()) {
            builder.append("> **交易动作**：").append(card.getTradeHint()).append("\n");
        }

        builder.append("> **提醒**：").append(card.getRiskHint()).append("\n\n")
                .append("<font color=\"comment\">仅供盘中研究与信息跟踪，不构成任何投资建议。</font>");
        return AStockEngagementMarkdown.appendRealtimeTail(builder.toString(), card.getStockName());
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
                    || trimmed.startsWith("AI 深度查股")
                    || trimmed.startsWith("持仓深度体检")
                    || trimmed.startsWith("👉")
                    || trimmed.contains("免责声明")) {
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
        String notice = MARKDOWN_TRUNCATION_NOTICE;
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
            builder.append("以下为续篇：\n\n");
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
