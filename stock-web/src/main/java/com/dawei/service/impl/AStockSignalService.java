package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A股公告信号规则服务：
 * 1. 过滤纯行政噪音公告
 * 2. 为保留公告计算事件类型、方向、信号分
 * 3. 生成批量公告聚类键，避免把财报打包披露当成多次异动
 */
@Component
public class AStockSignalService {

    private static final DateTimeFormatter CLUSTER_BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)(亿|万|%|亿元|万元)");
    private static final Pattern TITLE_CLEAN_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]");
    private static final Pattern ST_PREFIX_PATTERN = Pattern.compile("^(\\*st|st)[^:：]{0,12}[:：]", Pattern.CASE_INSENSITIVE);
    private static final List<String> ROUTINE_PLEDGE_OR_FREEZE_KEYWORDS = List.of(
            "股份质押", "股权质押", "解除质押", "补充质押", "质押展期",
            "质押及冻结", "股份冻结", "股权冻结", "轮候冻结"
    );
    private static final List<String> MATERIAL_FREEZE_CONTEXT_KEYWORDS = List.of(
            "司法冻结", "诉讼", "仲裁", "执行", "裁定", "法院", "保全"
    );
    private static final List<String> ROUTINE_BUYBACK_PROGRESS_KEYWORDS = List.of(
            "回购进展", "首次回购", "累计回购", "回购股份比例达到",
            "回购公司股份比例达到", "回购结果暨股份变动",
            "回购注销完成", "限制性股票回购注销完成", "调整转股价格",
            "转股价格调整", "转股价调整"
    );
    private static final List<String> CORRECTION_NOTICE_KEYWORDS = List.of(
            "更正公告", "补充公告", "修订说明", "更正后"
    );
    private static final List<String> RESTRUCTURING_CONTEXT_KEYWORDS = List.of(
            "重大资产重组", "购买资产", "发行股份", "支付现金购买资产",
            "并购", "重组", "收购", "关联交易"
    );
    private static final List<String> PRELIMINARY_RESTRUCTURING_KEYWORDS = List.of(
            "预案", "预披露", "意向协议", "意向书", "筹划", "停牌", "复牌",
            "一般风险提示", "风险提示", "提示性"
    );
    private static final List<String> ROUTINE_RESTRUCTURING_WRAPPER_KEYWORDS = List.of(
            "预案摘要", "相关方承诺事项", "承诺事项", "一般风险提示", "股票复牌", "公司股票复牌"
    );
    private static final List<String> MATERIAL_TRADING_RISK_KEYWORDS = List.of(
            "股票交易风险", "交易风险提示", "严重异常波动", "异动停牌核查"
    );
    private static final List<String> MATERIAL_INSOLVENCY_KEYWORDS = List.of(
            "债权人会议", "破产重整", "预重整", "重整计划", "破产清算", "管理人"
    );
    private static final List<String> MATERIAL_JUDICIAL_DISPOSAL_KEYWORDS = List.of(
            "司法拍卖", "司法处置", "拍卖进展", "拍卖结果", "拍卖完成",
            "公开拍卖", "变卖", "被动减持", "强制执行"
    );
    private static final List<String> MATERIAL_DELISTING_KEYWORDS = List.of(
            "终止上市", "可能被终止上市", "股票将被终止上市", "退市整理",
            "退市整理期", "财务类退市", "财务类强制退市", "实施退市风险警示", "退市风险警示"
    );
    private static final List<String> DELISTING_RELIEF_KEYWORDS = List.of(
            "申请撤销退市风险警示", "撤销退市风险警示申请",
            "申请撤销其他风险警示", "撤销其他风险警示申请",
            "撤销退市风险警示", "撤销其他风险警示", "申请摘帽", "摘帽"
    );
    private static final List<String> ST_RISK_CONTEXT_KEYWORDS = List.of(
            "风险提示", "退市", "终止上市", "异常波动", "立案", "处罚", "调查", "被实施"
    );

    private static final List<SignalRule> SIGNAL_RULES = List.of(
            new SignalRule("业绩兑现", "利多", 58, List.of("业绩预增", "预盈", "扭亏", "净利润增长", "同比增长", "业绩快报")),
            new SignalRule("重大合同", "利多", 55, List.of("重大合同", "中标", "订单", "项目中标", "定点通知", "中标候选")),
            new SignalRule("并购重组", "利多", 52, List.of("重大资产重组", "购买资产", "收购", "并购", "重组")),
            new SignalRule("产品获批", "利多", 48, List.of("获批", "注册证书", "临床试验批准", "认证", "FDA", "注册申请受理")),
            new SignalRule("回购增持", "利多", 42, List.of("回购", "增持")),
            new SignalRule("减持套现", "利空", 52, List.of("减持", "拟减持", "清仓减持")),
            new SignalRule("监管处罚", "利空", 58, List.of("立案", "处罚", "调查", "监管警示", "责令改正")),
            new SignalRule("退市风险", "利空", 64, List.of("终止上市", "可能被终止上市", "股票将被终止上市", "退市整理", "财务类退市", "财务类强制退市", "实施退市风险警示")),
            new SignalRule("重整风险", "利空", 56, List.of("债权人会议", "破产重整", "预重整", "重整计划", "破产清算", "管理人")),
            new SignalRule("司法处置", "利空", 54, List.of("司法拍卖", "司法处置", "拍卖进展", "拍卖结果", "拍卖完成", "公开拍卖", "变卖", "被动减持")),
            new SignalRule("诉讼仲裁", "利空", 52, List.of("诉讼", "仲裁", "司法冻结", "查封", "执行")),
            new SignalRule("业绩承压", "利空", 52, List.of("预亏", "亏损", "减值", "债务逾期", "退市风险")),
            new SignalRule("交易风险", "利空", 48, List.of("股票交易风险", "交易风险提示", "严重异常波动", "异动停牌核查")),
            new SignalRule("交易风险", "利空", 32, List.of("异常波动", "风险提示", "监管工作函", "问询函")),
            new SignalRule("资本动作", "中性", 28, List.of("签订协议", "投资设立", "进展"))
    );

    private final StockFilterConfig filterConfig;

    public AStockSignalService(StockFilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public boolean enrichNotice(AStockRss notice) {
        if (notice == null || notice.getTitle() == null || notice.getTitle().isBlank()) {
            return false;
        }

        String normalizedTagText = normalizeTagText(splitTagText(notice.getTag()));
        notice.setTag(normalizedTagText);

        if (isHardNoiseNotice(notice.getTitle(), normalizedTagText)) {
            notice.setEventType("行政公告");
            notice.setSignalSide("噪音");
            notice.setSignalScore(0);
            notice.setClusterKey(null);
            return false;
        }

        SignalDecision decision = evaluateSignal(notice.getTitle(), normalizedTagText);
        notice.setEventType(decision.eventType());
        notice.setSignalSide(decision.signalSide());
        notice.setSignalScore(decision.signalScore());
        notice.setClusterKey(buildClusterKey(notice, decision.eventType()));
        return true;
    }

    public boolean isRealtimeAlert(AStockRss notice) {
        return notice != null
                && notice.getSignalScore() != null
                && notice.getSignalScore() >= filterConfig.getARealtimeSignalThreshold();
    }

    public boolean meetsRankingThreshold(int signalScore) {
        return signalScore >= filterConfig.getARankingSignalThreshold();
    }

    public int getFetchPageCount() {
        return Math.max(1, filterConfig.getAFetchPageCount());
    }

    public boolean isPreferredEquityCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return false;
        }
        if (stockCode.startsWith("200") || stockCode.startsWith("900")) {
            return false;
        }
        return !stockCode.startsWith("110")
                && !stockCode.startsWith("111")
                && !stockCode.startsWith("113")
                && !stockCode.startsWith("118")
                && !stockCode.startsWith("123")
                && !stockCode.startsWith("127")
                && !stockCode.startsWith("128");
    }

    public List<String> splitTagText(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return List.of();
        }
        String[] parts = tagText.split("\\|");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    public String normalizeTagText(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return "未分类";
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag != null) {
                String trimmed = rawTag.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        return tags.isEmpty() ? "未分类" : String.join(" | ", tags);
    }

    public String buildClusterKey(AStockRss notice, String eventType) {
        LocalDateTime pubDate = notice.getPubDate() != null ? notice.getPubDate() : LocalDateTime.now();
        int window = Math.max(5, filterConfig.getAClusterWindowMinutes());
        int bucketMinute = (pubDate.getMinute() / window) * window;
        LocalDateTime bucket = pubDate.withMinute(bucketMinute).withSecond(0).withNano(0);
        if ("并购重组".equals(eventType) && isPreliminaryRestructuringNotice(notice.getTitle(), notice.getTag())) {
            return eventType + "|" + bucket.format(CLUSTER_BUCKET_FORMATTER) + "|preliminary_restructuring";
        }
        String stem = buildTitleStem(notice.getTitle(), notice.getStockName());
        return eventType + "|" + bucket.format(CLUSTER_BUCKET_FORMATTER) + "|" + stem;
    }

    private boolean isHardNoiseNotice(String title, String tagText) {
        if (isMaterialInsolvencyNotice(title, tagText)
                || isMaterialTradingRiskNotice(title, tagText)
                || isMaterialJudicialDisposalNotice(title, tagText)
                || isMaterialDelistingRiskNotice(title, tagText)) {
            return false;
        }
        if (isRoutinePledgeOrFreezeNotice(title, tagText)
                || isRoutineBuybackProgressNotice(title)
                || isCorrectionNotice(title)
                || isRoutineRestructuringWrapperNotice(title, tagText)) {
            return true;
        }
        if (containsStrongSignalKeyword(title, tagText)) {
            return false;
        }
        return filterConfig.containsBlacklistKeyword(title)
                || filterConfig.containsAnyTagKeyword(tagText, filterConfig.getTrashTagKeywords());
    }

    private boolean containsStrongSignalKeyword(String title, String tagText) {
        String combined = combine(title, tagText);
        return SIGNAL_RULES.stream()
                .filter(rule -> !"中性".equals(rule.signalSide()))
                .anyMatch(rule -> rule.matches(combined));
    }

    private SignalDecision evaluateSignal(String title, String tagText) {
        String combined = combine(title, tagText);
        boolean materialTradingRisk = isMaterialTradingRiskNotice(title, tagText);
        boolean materialInsolvency = isMaterialInsolvencyNotice(title, tagText);
        boolean materialJudicialDisposal = isMaterialJudicialDisposalNotice(title, tagText);
        boolean materialDelistingRisk = isMaterialDelistingRiskNotice(title, tagText);
        boolean stRiskStock = hasStRiskPrefix(title);
        boolean materialRiskNotice = materialTradingRisk
                || materialInsolvency
                || materialJudicialDisposal
                || materialDelistingRisk;

        int bullishScore = 0;
        int bearishScore = 0;
        int neutralScore = 0;
        String eventType = "常规事项";

        for (SignalRule rule : SIGNAL_RULES) {
            if (!rule.matches(combined)) {
                continue;
            }
            if ("利多".equals(rule.signalSide())) {
                bullishScore = Math.max(bullishScore, rule.weight());
            } else if ("利空".equals(rule.signalSide())) {
                bearishScore = Math.max(bearishScore, rule.weight());
            } else {
                neutralScore = Math.max(neutralScore, rule.weight());
            }
            if ("常规事项".equals(eventType)) {
                eventType = rule.eventType();
            }
        }

        String signalSide = resolveSignalSide(bullishScore, bearishScore, neutralScore);
        int score = 20 + Math.max(Math.max(bullishScore, bearishScore), neutralScore);

        if (AMOUNT_PATTERN.matcher(title).find()) {
            score += 8;
        }
        if (filterConfig.containsAnyTagKeyword(tagText, filterConfig.getGrayTagKeywords()) && !materialRiskNotice) {
            score -= 12;
        }
        if ((title.contains("进展公告") || title.contains("提示性公告") || title.contains("回复公告")) && !materialRiskNotice) {
            score -= 10;
        }
        if ("并购重组".equals(eventType) && isPreliminaryRestructuringNotice(title, tagText)) {
            score -= 20;
            signalSide = "中性";
        }
        if (isRestructuringWrapperNotice(title, tagText)) {
            score -= 12;
        }
        if ("并购重组".equals(eventType) && (title.contains("停牌") || title.contains("复牌"))) {
            score -= 8;
        }
        if ("常规事项".equals(eventType) && tagText.contains("其他")) {
            score -= 8;
        }
        if ("常规事项".equals(eventType) && "中性".equals(signalSide)) {
            score = Math.min(score, 38);
        }
        if (stRiskStock && "利空".equals(signalSide)) {
            score = Math.max(score, 74);
        }
        if (materialJudicialDisposal) {
            eventType = "司法处置";
            signalSide = "利空";
            score = Math.max(score, 72);
        }
        if (materialDelistingRisk) {
            eventType = "退市风险";
            signalSide = "利空";
            score = Math.max(score, 84);
        }
        if (materialTradingRisk) {
            eventType = "交易风险";
            signalSide = "利空";
            score = Math.max(score, 68);
        }

        return new SignalDecision(eventType, signalSide, Math.max(5, Math.min(score, 99)));
    }

    private boolean isRoutinePledgeOrFreezeNotice(String title, String tagText) {
        String combined = combine(title, tagText);
        return containsAny(combined, ROUTINE_PLEDGE_OR_FREEZE_KEYWORDS)
                && !containsAny(combined, MATERIAL_FREEZE_CONTEXT_KEYWORDS);
    }

    private boolean isRoutineBuybackProgressNotice(String title) {
        return containsAny(combine(title, null), ROUTINE_BUYBACK_PROGRESS_KEYWORDS);
    }

    private boolean isCorrectionNotice(String title) {
        return containsAny(combine(title, null), CORRECTION_NOTICE_KEYWORDS);
    }

    private boolean isMaterialTradingRiskNotice(String title, String tagText) {
        return containsAny(combine(title, tagText), MATERIAL_TRADING_RISK_KEYWORDS);
    }

    private boolean isMaterialInsolvencyNotice(String title, String tagText) {
        return containsAny(combine(title, tagText), MATERIAL_INSOLVENCY_KEYWORDS);
    }

    private boolean isMaterialJudicialDisposalNotice(String title, String tagText) {
        return containsAny(combine(title, tagText), MATERIAL_JUDICIAL_DISPOSAL_KEYWORDS);
    }

    private boolean isMaterialDelistingRiskNotice(String title, String tagText) {
        String combined = combine(title, tagText);
        if (containsAny(combined, DELISTING_RELIEF_KEYWORDS)) {
            return false;
        }
        return containsAny(combined, MATERIAL_DELISTING_KEYWORDS)
                || (hasStRiskPrefix(title) && containsAny(combined, ST_RISK_CONTEXT_KEYWORDS));
    }

    private boolean hasStRiskPrefix(String title) {
        return title != null && ST_PREFIX_PATTERN.matcher(title.trim()).find();
    }

    private boolean isPreliminaryRestructuringNotice(String title, String tagText) {
        String combined = combine(title, tagText);
        return containsAny(combined, RESTRUCTURING_CONTEXT_KEYWORDS)
                && containsAny(combined, PRELIMINARY_RESTRUCTURING_KEYWORDS);
    }

    private boolean isRestructuringWrapperNotice(String title, String tagText) {
        String combined = combine(title, tagText);
        return containsAny(combined, RESTRUCTURING_CONTEXT_KEYWORDS)
                && (combined.contains("一般风险提示") || combined.contains("复牌") || combined.contains("停牌"));
    }

    private boolean isRoutineRestructuringWrapperNotice(String title, String tagText) {
        String combined = combine(title, tagText);
        return containsAny(combined, RESTRUCTURING_CONTEXT_KEYWORDS)
                && containsAny(combined, ROUTINE_RESTRUCTURING_WRAPPER_KEYWORDS);
    }

    private String resolveSignalSide(int bullishScore, int bearishScore, int neutralScore) {
        if (bullishScore >= bearishScore + 10 && bullishScore > 0) {
            return "利多";
        }
        if (bearishScore >= bullishScore + 10 && bearishScore > 0) {
            return "利空";
        }
        if (neutralScore > 0) {
            return "中性";
        }
        return bullishScore == bearishScore ? "中性" : (bullishScore > bearishScore ? "利多" : "利空");
    }

    private String buildTitleStem(String title, String stockName) {
        String stem = title == null ? "" : title;
        if (stockName != null && !stockName.isBlank() && stem.startsWith(stockName + ":")) {
            stem = stem.substring(stockName.length() + 1);
        }
        int colonIndex = stem.indexOf(':');
        if (colonIndex > 0 && colonIndex < 12) {
            stem = stem.substring(colonIndex + 1);
        }

        stem = stem.replace("关于", "")
                .replace("股份有限公司", "")
                .replace("有限公司", "")
                .replace("公司", "")
                .replace("公告", "")
                .replace("提示性", "")
                .replace("进展", "")
                .replace("回复报告", "")
                .replace("回复", "")
                .replace("专项核查意见", "")
                .replace("核查意见", "")
                .replace("法律意见书", "")
                .replace("审计报告", "")
                .replace("报告书", "")
                .replace("摘要", "");
        stem = TITLE_CLEAN_PATTERN.matcher(stem).replaceAll("");
        if (stem.isBlank()) {
            stem = "common";
        }
        return stem.length() > 24 ? stem.substring(0, 24) : stem;
    }

    private String combine(String title, String tagText) {
        return ((title == null ? "" : title) + " " + (tagText == null ? "" : tagText)).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(text::contains);
    }

    private record SignalRule(String eventType, String signalSide, int weight, List<String> keywords) {
        boolean matches(String text) {
            return keywords.stream()
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .anyMatch(text::contains);
        }
    }

    private record SignalDecision(String eventType, String signalSide, int signalScore) {
    }
}
