package com.dawei.utils;

import com.dawei.entity.AStockMsg;
import com.dawei.entity.USStockMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @ClassName WeComApi
 * @Author 风间影月
 * @Version 1.0
 * @Description 企业微信机器人 Webhook API
 **/
@Slf4j
@Service
public class WeComApi {

    @Value("${WECOM_WEBHOOK_URL}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public WeComApi() {}

    public WeComApi(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * @Description: 格式化单条股票消息
     * @Author 风间影月
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
     * @Author 风间影月
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
     * @Author 风间影月
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
     * @Author 风间影月
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
     * @Description: 发送 Markdown 格式消息到企业微信
     * @Author 风间影月
     * @param markdownContent
     */
    public void sendMarkdownMessage(String markdownContent) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("企业微信 webhook URL 未配置，跳过发送");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", markdownContent);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");
            requestBody.put("markdown", markdown);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(webhookUrl, request, String.class);
            
            log.info("企业微信消息发送成功，响应: {}", response);
        } catch (Exception e) {
            log.error("企业微信消息发送失败: {}", e.getMessage(), e);
            throw new RuntimeException("企业微信消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * @Description: 发送文本消息到企业微信
     * @Author 风间影月
     * @param textContent
     */
    public void sendTextMessage(String textContent) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("企业微信 webhook URL 未配置，跳过发送");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> text = new HashMap<>();
            text.put("content", textContent);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "text");
            requestBody.put("text", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(webhookUrl, request, String.class);
            
            log.info("企业微信消息发送成功，响应: {}", response);
        } catch (Exception e) {
            log.error("企业微信消息发送失败: {}", e.getMessage(), e);
            throw new RuntimeException("企业微信消息发送失败: " + e.getMessage(), e);
        }
    }

    // ============== A股相关方法 ==============

    /**
     * @Description: 格式化单条A股公告消息
     * @Author 风间影月
     * @param aStockMsg
     */
    public String formatAStockInfo(AStockMsg aStockMsg) {
        return "<font color=\"info\">📌 代码：</font>" + aStockMsg.getStockCode() + "\n" +
               "<font color=\"info\">🏢 名称：</font>" + aStockMsg.getStockName() + "\n" +
               "<font color=\"comment\">📅 时间：</font>" + aStockMsg.getPubDate() + "\n" +
               "<font color=\"comment\">📰 标题：</font>" + aStockMsg.getTitle() + "\n" +
               "<font color=\"warning\">🏷️ 类型：</font>" + aStockMsg.getTag() + "\n" +
               "<font color=\"comment\">📊 统计：</font>24小时=" + aStockMsg.getCounts24Hour() + "次; " +
               "3天内=" + aStockMsg.getCounts3Day() + "次; " +
               "1周内=" + aStockMsg.getCounts1Week() + "次";
    }

    /**
     * @Description: 格式化多条A股公告消息
     * @Author 风间影月
     * @param stocks
     */
    public String formatAStockInfoFromList(List<AStockMsg> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return "暂无A股公告信息";
        }
        String header = "## 📊 A股最新公告\n\n";
        return header + stocks.stream()
                .map(this::formatAStockInfo)
                .collect(Collectors.joining("\n\n----------\n\n"));
    }
}
