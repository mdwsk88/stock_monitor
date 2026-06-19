package com.dawei.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.WeComFeedback;
import com.dawei.entity.WeComSubscription;
import com.dawei.mapper.WeComFeedbackMapper;
import com.dawei.mapper.WeComSubscriptionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 企业微信群产品闭环工具：关注池与轻反馈。
 */
@Component
@Slf4j
public class AStockEngagementTool {

    @Resource
    private WeComSubscriptionMapper weComSubscriptionMapper;

    @Resource
    private WeComFeedbackMapper weComFeedbackMapper;

    @Tool(description = "当用户说关注、订阅、继续关注某个A股主题或股票时调用。"
            + "这是群级关注池，不是个人持仓建议；只用于后续推送更贴近用户关心的主题或股票。")
    public Map<String, Object> followAStockTarget(
            @ToolParam(description = "关注类型：THEME 或 STOCK；不确定时传空") String subscriptionType,
            @ToolParam(description = "主题名或股票名，例如低空经济、算力、宗申动力") String targetName,
            @ToolParam(description = "股票代码；关注主题时可为空") String stockCode,
            @ToolParam(description = "关注原因，可用用户原话或系统摘要") String reason) {
        String normalizedTarget = StringUtils.trimToNull(targetName);
        if (normalizedTarget == null) {
            return Map.of("success", false, "message", "关注失败：缺少主题名或股票名");
        }
        String normalizedStockCode = StringUtils.defaultString(StringUtils.trimToNull(stockCode));
        String normalizedType = normalizeSubscriptionType(subscriptionType, normalizedStockCode);
        WeComSubscription existing = findSubscription(normalizedType, normalizedTarget, normalizedStockCode);
        boolean created = existing == null;
        LocalDateTime now = LocalDateTime.now();
        WeComSubscription subscription = created ? new WeComSubscription() : existing;
        if (created) {
            subscription.setId(UUID.randomUUID().toString().replace("-", ""));
            subscription.setCreateTime(now);
        }
        subscription.setSubscriptionType(normalizedType);
        subscription.setTargetName(normalizedTarget);
        subscription.setStockCode(normalizedStockCode);
        subscription.setEnabled(1);
        subscription.setSource("wecom-mcp");
        subscription.setReason(StringUtils.trimToNull(reason));
        subscription.setUpdateTime(now);
        if (created) {
            weComSubscriptionMapper.insert(subscription);
        } else {
            weComSubscriptionMapper.updateById(subscription);
        }
        log.info("企业微信群关注项已保存，type={}, target={}, stockCode={}",
                normalizedType, normalizedTarget, normalizedStockCode);
        return Map.of(
                "success", true,
                "message", normalizedTarget + " 已加入群级关注池，后续推送会优先解释相关变化。",
                "subscription", subscription
        );
    }

    @Tool(description = "当用户明确说取消关注、暂停关注或不想再看某个A股主题/股票时调用。")
    public Map<String, Object> muteAStockTarget(
            @ToolParam(description = "关注类型：THEME 或 STOCK；不确定时传空") String subscriptionType,
            @ToolParam(description = "主题名或股票名") String targetName,
            @ToolParam(description = "股票代码；没有可为空") String stockCode) {
        String normalizedTarget = StringUtils.trimToNull(targetName);
        if (normalizedTarget == null) {
            return Map.of("success", false, "message", "暂停关注失败：缺少主题名或股票名");
        }
        String normalizedStockCode = StringUtils.defaultString(StringUtils.trimToNull(stockCode));
        String normalizedType = normalizeSubscriptionType(subscriptionType, normalizedStockCode);
        WeComSubscription subscription = findSubscription(normalizedType, normalizedTarget, normalizedStockCode);
        if (subscription == null) {
            return Map.of("success", false, "message", "未找到对应关注项");
        }
        subscription.setEnabled(0);
        subscription.setUpdateTime(LocalDateTime.now());
        weComSubscriptionMapper.updateById(subscription);
        return Map.of("success", true, "message", normalizedTarget + " 已暂停主动关注。");
    }

    @Tool(description = "查看当前企业微信群级关注池。适合用户问现在关注了哪些主题或股票时调用。")
    public List<WeComSubscription> listAStockSubscriptions(
            @ToolParam(description = "是否只看启用项，默认 true") Boolean enabledOnly,
            @ToolParam(description = "返回数量，默认 20，最大 50") Integer limit) {
        int resolvedLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        QueryWrapper<WeComSubscription> queryWrapper = new QueryWrapper<>();
        if (enabledOnly == null || enabledOnly) {
            queryWrapper.eq("enabled", 1);
        }
        queryWrapper.orderByDesc("enabled")
                .orderByDesc("update_time")
                .last("LIMIT " + resolvedLimit);
        return weComSubscriptionMapper.selectList(queryWrapper);
    }

    @Tool(description = "当用户对推送说有用、太吵、太晚、不相关、继续关注时调用，用于记录轻反馈并优化后续推送。")
    public Map<String, Object> recordAStockPushFeedback(
            @ToolParam(description = "反馈类型：USEFUL/NOISY/LATE/IRRELEVANT/FOLLOW，或中文：有用/太吵/太晚/不相关/继续关注") String feedbackType,
            @ToolParam(description = "反馈对象类型：PUSH/THEME/STOCK；不确定可为空") String targetType,
            @ToolParam(description = "反馈对象名称，例如低空经济、宗申动力、盘前早报") String targetName,
            @ToolParam(description = "股票代码；没有可为空") String stockCode,
            @ToolParam(description = "主题名；没有可为空") String themeName,
            @ToolParam(description = "用户补充说明或原话") String comment) {
        WeComFeedback feedback = new WeComFeedback();
        feedback.setId(UUID.randomUUID().toString().replace("-", ""));
        feedback.setFeedbackType(normalizeFeedbackType(feedbackType));
        feedback.setTargetType(normalizeTargetType(targetType));
        feedback.setTargetName(StringUtils.trimToNull(targetName));
        feedback.setStockCode(StringUtils.trimToNull(stockCode));
        feedback.setThemeName(StringUtils.trimToNull(themeName));
        feedback.setSource("wecom-mcp");
        feedback.setComment(StringUtils.trimToNull(comment));
        feedback.setCreateTime(LocalDateTime.now());
        weComFeedbackMapper.insert(feedback);
        log.info("企业微信群轻反馈已记录，type={}, target={}", feedback.getFeedbackType(), feedback.getTargetName());
        return Map.of(
                "success", true,
                "message", resolveFeedbackMessage(feedback.getFeedbackType()),
                "feedbackType", feedback.getFeedbackType()
        );
    }

    private WeComSubscription findSubscription(String subscriptionType, String targetName, String stockCode) {
        return weComSubscriptionMapper.selectOne(new QueryWrapper<WeComSubscription>()
                .eq("subscription_type", subscriptionType)
                .eq("target_name", targetName)
                .eq("stock_code", StringUtils.defaultString(stockCode))
                .last("LIMIT 1"));
    }

    private String normalizeSubscriptionType(String subscriptionType, String stockCode) {
        String normalized = StringUtils.defaultString(subscriptionType).trim().toUpperCase(Locale.ROOT);
        if ("STOCK".equals(normalized) || "股票".equals(normalized)) {
            return "STOCK";
        }
        if ("THEME".equals(normalized) || "主题".equals(normalized) || "题材".equals(normalized)) {
            return "THEME";
        }
        return StringUtils.isNotBlank(stockCode) ? "STOCK" : "THEME";
    }

    private String normalizeFeedbackType(String feedbackType) {
        String normalized = StringUtils.defaultString(feedbackType).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "USEFUL", "有用" -> "USEFUL";
            case "NOISY", "太吵" -> "NOISY";
            case "LATE", "太晚" -> "LATE";
            case "IRRELEVANT", "不相关" -> "IRRELEVANT";
            case "FOLLOW", "继续关注" -> "FOLLOW";
            default -> "OTHER";
        };
    }

    private String normalizeTargetType(String targetType) {
        String normalized = StringUtils.defaultString(targetType).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK", "股票" -> "STOCK";
            case "THEME", "主题", "题材" -> "THEME";
            case "PUSH", "推送" -> "PUSH";
            default -> "UNKNOWN";
        };
    }

    private String resolveFeedbackMessage(String feedbackType) {
        return switch (feedbackType) {
            case "USEFUL" -> "收到，这条推送已标记为有用，后续同类高质量信号会保留权重。";
            case "NOISY" -> "收到，已标记为太吵，后续会作为上调阈值的依据。";
            case "LATE" -> "收到，已标记为太晚，后续会统计事件时效。";
            case "IRRELEVANT" -> "收到，已标记为不相关，后续会降低类似主题的推送优先级。";
            case "FOLLOW" -> "收到，已记录继续关注意图。";
            default -> "收到，反馈已记录。";
        };
    }
}
