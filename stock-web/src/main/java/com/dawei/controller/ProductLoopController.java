package com.dawei.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.WeComFeedback;
import com.dawei.entity.WeComSubscription;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.mapper.WeComFeedbackMapper;
import com.dawei.mapper.WeComSubscriptionMapper;
import com.dawei.utils.AStockEngagementMarkdown;
import com.dawei.utils.WeComApi;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 产品闭环原型接口：关注、反馈、演示推送和轻量指标。
 */
@Slf4j
@RestController
@RequestMapping("/api/product-loop")
public class ProductLoopController {

    private static final int RECENT_LIMIT = 12;

    private final WeComFeedbackMapper weComFeedbackMapper;
    private final WeComSubscriptionMapper weComSubscriptionMapper;
    private final AStockPushLogMapper aStockPushLogMapper;
    private final WeComApi weComApi;

    public ProductLoopController(WeComFeedbackMapper weComFeedbackMapper,
                                 WeComSubscriptionMapper weComSubscriptionMapper,
                                 AStockPushLogMapper aStockPushLogMapper,
                                 WeComApi weComApi) {
        this.weComFeedbackMapper = weComFeedbackMapper;
        this.weComSubscriptionMapper = weComSubscriptionMapper;
        this.aStockPushLogMapper = aStockPushLogMapper;
        this.weComApi = weComApi;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<WeComFeedback> feedback = weComFeedbackMapper.selectList(new QueryWrapper<WeComFeedback>()
                .ge("create_time", sevenDaysAgo)
                .orderByDesc("create_time")
                .last("LIMIT 200"));
        List<WeComSubscription> subscriptions = weComSubscriptionMapper.selectList(new QueryWrapper<WeComSubscription>()
                .orderByDesc("enabled")
                .orderByDesc("update_time")
                .last("LIMIT 50"));
        List<AStockPushLog> recentPushes = aStockPushLogMapper.selectList(new QueryWrapper<AStockPushLog>()
                .select("push_type", "stock_code", "stock_name", "macro_theme_name", "signal_side", "signal_score", "title", "pushed_at")
                .orderByDesc("pushed_at")
                .last("LIMIT " + RECENT_LIMIT));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("generatedAt", LocalDateTime.now());
        result.put("feedbackStats", buildFeedbackStats(feedback));
        result.put("subscriptions", subscriptions);
        result.put("recentPushes", recentPushes);
        result.put("demoMessages", List.of("morning", "intraday", "risk", "feedback"));
        return result;
    }

    @PostMapping("/feedback")
    public Map<String, Object> recordFeedback(@RequestBody FeedbackRequest request) {
        WeComFeedback feedback = new WeComFeedback();
        feedback.setId(UUID.randomUUID().toString().replace("-", ""));
        feedback.setFeedbackType(normalizeFeedbackType(request.getFeedbackType()));
        feedback.setTargetType(normalizeTargetType(request.getTargetType()));
        feedback.setTargetName(StringUtils.trimToNull(request.getTargetName()));
        feedback.setStockCode(StringUtils.trimToNull(request.getStockCode()));
        feedback.setThemeName(StringUtils.trimToNull(request.getThemeName()));
        feedback.setPushType(StringUtils.trimToNull(request.getPushType()));
        feedback.setPushKey(StringUtils.trimToNull(request.getPushKey()));
        feedback.setSource(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.getSource()), "product-prototype"));
        feedback.setComment(StringUtils.trimToNull(request.getComment()));
        feedback.setCreateTime(LocalDateTime.now());
        weComFeedbackMapper.insert(feedback);

        return Map.of(
                "success", true,
                "message", "反馈已记录",
                "feedback", feedback
        );
    }

    @PostMapping("/subscriptions")
    public Map<String, Object> upsertSubscription(@RequestBody SubscriptionRequest request) {
        String targetName = StringUtils.trimToNull(request.getTargetName());
        if (targetName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetName 不能为空");
        }
        String stockCode = StringUtils.defaultString(StringUtils.trimToNull(request.getStockCode()));
        String subscriptionType = normalizeSubscriptionType(request.getSubscriptionType(), stockCode);
        WeComSubscription subscription = findSubscription(subscriptionType, targetName, stockCode);
        boolean newSubscription = subscription == null;
        LocalDateTime now = LocalDateTime.now();
        if (newSubscription) {
            subscription = new WeComSubscription();
            subscription.setId(UUID.randomUUID().toString().replace("-", ""));
            subscription.setCreateTime(now);
        }
        subscription.setSubscriptionType(subscriptionType);
        subscription.setTargetName(targetName);
        subscription.setStockCode(stockCode);
        subscription.setEnabled(request.getEnabled() == null || request.getEnabled() > 0 ? 1 : 0);
        subscription.setSource(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.getSource()), "product-prototype"));
        subscription.setReason(StringUtils.trimToNull(request.getReason()));
        subscription.setUpdateTime(now);

        if (newSubscription) {
            weComSubscriptionMapper.insert(subscription);
        } else {
            weComSubscriptionMapper.updateById(subscription);
        }

        return Map.of(
                "success", true,
                "message", subscription.getEnabled() > 0 ? "关注项已启用" : "关注项已暂停",
                "subscription", subscription
        );
    }

    @GetMapping("/demo-message")
    public Map<String, Object> demoMessage(@RequestParam(defaultValue = "morning") String type) {
        String resolvedType = normalizeDemoType(type);
        return Map.of(
                "success", true,
                "type", resolvedType,
                "markdown", buildDemoMarkdown(resolvedType)
        );
    }

    @PostMapping("/demo-push")
    public Map<String, Object> demoPush(@RequestParam(defaultValue = "morning") String type) {
        String resolvedType = normalizeDemoType(type);
        String markdown = buildDemoMarkdown(resolvedType);
        weComApi.sendMarkdownMessage(markdown, WeComApi.MarketType.A);
        log.info("产品闭环演示推送已发送，type={}", resolvedType);
        return Map.of(
                "success", true,
                "message", "演示推送已发送到 A 股企业微信群",
                "type", resolvedType
        );
    }

    private Map<String, Object> buildFeedbackStats(List<WeComFeedback> feedback) {
        Map<String, Long> byType = feedback.stream()
                .collect(Collectors.groupingBy(WeComFeedback::getFeedbackType, LinkedHashMap::new, Collectors.counting()));
        long total = feedback.size();
        long useful = byType.getOrDefault("USEFUL", 0L);
        long noisy = byType.getOrDefault("NOISY", 0L);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("byType", byType);
        stats.put("usefulRate", total == 0 ? 0 : Math.round(useful * 1000.0 / total) / 10.0);
        stats.put("noiseRate", total == 0 ? 0 : Math.round(noisy * 1000.0 / total) / 10.0);
        return stats;
    }

    private WeComSubscription findSubscription(String subscriptionType, String targetName, String stockCode) {
        QueryWrapper<WeComSubscription> queryWrapper = new QueryWrapper<WeComSubscription>()
                .eq("subscription_type", subscriptionType)
                .eq("target_name", targetName);
        queryWrapper.eq("stock_code", StringUtils.defaultString(stockCode));
        return weComSubscriptionMapper.selectOne(queryWrapper.last("LIMIT 1"));
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

    private String normalizeDemoType(String type) {
        String normalized = StringUtils.defaultString(type).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "intraday", "risk", "feedback" -> normalized;
            default -> "morning";
        };
    }

    private String buildDemoMarkdown(String type) {
        String today = LocalDateTime.now().toLocalDate().toString();
        String markdown = switch (type) {
            case "intraday" -> "# ⚡ 盘中风口瞬时共振 | " + today + "\n\n"
                    + "> **突发事件**：低空经济试点政策出现新增推进信号\n"
                    + "> **为什么现在**：宏观快讯命中历史高分公告和群级关注池，不是普通新闻转发\n"
                    + "> **AI 记忆库匹配**：宗申动力、中信海直、万丰奥威均存在近期主题映射\n"
                    + "> **提醒**：盘中催化只代表关注度升温，需要结合板块承接确认。";
            case "risk" -> "# 🌇 A股盘后风险速递 | " + today + "\n\n"
                    + "> **结论**：收盘后公告窗口出现退市风险、监管处罚、减持三类硬风险\n"
                    + "> **为什么现在**：这类公告常在 15:00 后集中披露，适合盘后统一处理\n"
                    + "> **关注池命中**：2 条风险与群级关注项相关，建议明早优先复查。";
            case "feedback" -> "# 📊 推送质量日报 | " + today + "\n\n"
                    + "> **互动**：今日演示推送产生多次追问，最高频问题是“为什么上榜”\n"
                    + "> **反馈**：有用率、噪音率会进入运营看板，用来校准阈值\n"
                    + "> **产品结论**：用户更需要“我的关注项发生了什么”，而不是泛泛市场广播。";
            default -> "# 🌅 A股盘前研究卡 | " + today + "\n\n"
                    + "> **结论**：今日主线候选偏向低空经济与算力，但需要开盘后确认扩散\n"
                    + "> **为什么现在**：过去 24 小时政策线索和高分公告同时出现\n"
                    + "> **关键证据**：低空经济主题强度 96 分，算力主题强度 92 分\n"
                    + "> **反向风险**：如果开盘 30 分钟内没有板块扩散，只保留核心观察。";
        };
        return AStockEngagementMarkdown.appendReportTail(markdown);
    }

    @Data
    public static class FeedbackRequest {
        private String feedbackType;
        private String targetType;
        private String targetName;
        private String stockCode;
        private String themeName;
        private String pushType;
        private String pushKey;
        private String source;
        private String comment;
    }

    @Data
    public static class SubscriptionRequest {
        private String subscriptionType;
        private String targetName;
        private String stockCode;
        private Integer enabled;
        private String source;
        private String reason;
    }
}
