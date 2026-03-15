package com.dawei.utils;

import com.dawei.entity.AStockMsg;
import com.dawei.entity.USStockMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> contentBody = new HashMap<>();
            contentBody.put("content", content);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", messageType);
            requestBody.put(messageType, contentBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(webhookUrl, request, String.class);
            log.info("企业微信消息发送成功（市场: {}，类型: {}），响应: {}", marketType, messageType, response);
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
        return "<font color=\"info\">📌 代码：</font>" + aStockMsg.getStockCode() + "\n" +
               "<font color=\"info\">🏢 名称：</font>" + aStockMsg.getStockName() + "\n" +
               "<font color=\"comment\">📅 时间：</font>" + aStockMsg.getPubDate() + "\n" +
               "<font color=\"comment\">📰 标题：</font>" + aStockMsg.getTitle() + "\n" +
               "<font color=\"warning\">🏷️ 类型：</font>" + aStockMsg.getTag() + "\n" +
               "<font color=\"comment\">🧭 事件：</font>" + aStockMsg.getEventType() + " / " + aStockMsg.getSignalSide() + "\n" +
               "<font color=\"comment\">🎯 评分：</font>" + aStockMsg.getSignalScore() + " 分\n" +
               "<font color=\"comment\">📊 支撑：</font>24小时公告=" + aStockMsg.getCounts24Hour() + "条; " +
               "3天内公告=" + aStockMsg.getCounts3Day() + "条; " +
               "1周内公告=" + aStockMsg.getCounts1Week() + "条";
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
}
