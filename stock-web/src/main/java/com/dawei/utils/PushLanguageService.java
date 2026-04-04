package com.dawei.utils;

import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 企业微信推送语言开关与常用本地化映射。
 */
@Service
public class PushLanguageService {

    private static final ThreadLocal<String> LANGUAGE_OVERRIDE = new ThreadLocal<>();

    private static final Map<String, String> EVENT_TYPE_EN = Map.ofEntries(
            Map.entry("重大合同", "Major Contract"),
            Map.entry("并购重组", "M&A / Restructuring"),
            Map.entry("业绩兑现", "Earnings Delivery"),
            Map.entry("产品获批", "Product Approval"),
            Map.entry("回购增持", "Buyback / Insider Increase"),
            Map.entry("退市风险", "Delisting Risk"),
            Map.entry("监管处罚", "Regulatory Penalty"),
            Map.entry("重整风险", "Restructuring Risk"),
            Map.entry("司法处置", "Judicial Disposal"),
            Map.entry("诉讼仲裁", "Litigation / Arbitration"),
            Map.entry("业绩承压", "Earnings Pressure"),
            Map.entry("交易风险", "Trading Risk"),
            Map.entry("流动性收紧", "Liquidity Tightening"),
            Map.entry("监管收紧", "Regulatory Tightening"),
            Map.entry("贸易摩擦", "Trade Friction"),
            Map.entry("地缘与商品冲击", "Geopolitical / Commodity Shock"),
            Map.entry("价格承压", "Price Pressure"),
            Map.entry("政策发布", "Policy Release"),
            Map.entry("流动性宽松", "Liquidity Easing"),
            Map.entry("政策扶持", "Policy Support"),
            Map.entry("价格催化", "Price Catalyst"),
            Map.entry("景气催化", "Cycle Catalyst")
    );

    private static final Map<String, String> POSITION_REASON_EN = Map.ofEntries(
            Map.entry("事件评分进入主线级", "Event score reached main-theme tier"),
            Map.entry("事件评分处于高优先级", "Event score is in the high-priority tier"),
            Map.entry("具备边际进攻弹性", "Has marginal upside trading elasticity"),
            Map.entry("支撑公告密集", "Dense notice support"),
            Map.entry("公告支撑数量尚可", "Notice support count is decent"),
            Map.entry("存在多事件簇抬升辨识度", "Multiple event clusters improve visibility"),
            Map.entry("命中宏观主线共振", "Matched macro-theme resonance"),
            Map.entry("催化类型具备兑现性", "Catalyst type has execution quality"),
            Map.entry("催化更偏题材映射而非硬兑现", "Catalyst is more theme-mapping than hard execution"),
            Map.entry("防守态下独立催化承接偏弱", "Standalone catalyst follow-through is weaker in a defensive regime"),
            Map.entry("进攻态提高资金容错率", "Risk-on regime improves market tolerance"),
            Map.entry("高潮态下核心共振仍具辨识度", "Core resonance still stands out in an overheated regime"),
            Map.entry("高潮态下后排追高容错下降", "Overheated regime lowers tolerance for chasing laggards"),
            Map.entry("当前仅保留为边际观察，不宜直接升级为主线锚点", "Keep on the watchlist only; not ready to be promoted to a main-theme anchor")
    );

    private static final Map<String, String> TRADE_HINT_EN = Map.ofEntries(
            Map.entry("只跟踪分歧承接与换手强度，不建议在高潮尾段无脑追价", "Track only pullback support and turnover quality; do not blindly chase late in an overheated move"),
            Map.entry("仅在逆势走强或获得主线确认时观察，不作为常规追涨标的", "Watch only if it stays strong against the tape or gets confirmed by the main theme; not a routine chase candidate"),
            Map.entry("可作为主线锚点，优先观察开盘承接、量能和主题扩散", "Can serve as a main-theme anchor; focus on opening support, volume, and theme expansion"),
            Map.entry("可作为强催化主攻方向，但仍需确认板块资金是否跟随", "Can be treated as a high-conviction catalyst, but sector follow-through still needs confirmation"),
            Map.entry("更依赖板块延续，后排追高风险更高，宜等分歧确认", "Relies more on sector continuation; chasing laggards is riskier, so wait for confirmation after pullbacks"),
            Map.entry("缺少逆势属性，更多作为题材温度计，不宜重仓博弈", "Lacks anti-fragile strength and is better used as a sector thermometer than a heavy position trade"),
            Map.entry("更适合作为主线扩散补涨标的，需结合龙头强度同步观察", "Better suited as a secondary lagging play in a spreading theme; track it together with the leader's strength"),
            Map.entry("公告有边际催化，但尚未形成主线锚定，先放入观察名单", "The notice provides a marginal catalyst, but it has not yet anchored a main theme; keep it on the watchlist first"),
            Map.entry("情绪过热阶段更易掉队，若无新增强化，尽量避免尾盘追涨", "Names are more likely to lag in an overheated tape; avoid chasing late without fresh reinforcement"),
            Map.entry("先观察是否获得量能、主线和公告兑现的二次确认", "Wait for a second confirmation from volume, theme strength, and catalyst follow-through")
    );

    private static final Map<String, String> RELATION_REASON_EN = Map.ofEntries(
            Map.entry("宏观主题与公告事件类型共振", "Macro theme resonates with the notice event type"),
            Map.entry("宏观主题关键词与公告主题一致", "Macro-theme keywords align with the notice theme"),
            Map.entry("宏观主题与公告标的发生映射共振", "Macro theme resonates through mapped exposure"),
            Map.entry("公告直接命中主线", "The notice directly hits the active macro theme")
    );

    @Value("${stock.push.language:zh}")
    private String pushLanguage = "zh";

    public PushLanguageService() {
    }

    public PushLanguageService(String pushLanguage) {
        this.pushLanguage = pushLanguage;
    }

    public boolean isEnglish() {
        return "en".equalsIgnoreCase(currentLanguage());
    }

    public String currentLanguage() {
        String override = normalizeLanguage(LANGUAGE_OVERRIDE.get());
        if (override != null) {
            return override;
        }
        String configured = normalizeLanguage(pushLanguage);
        return configured != null ? configured : "zh";
    }

    public <T> T withLanguage(String language, Supplier<T> action) {
        String normalized = normalizeLanguage(language);
        if (normalized == null) {
            return action.get();
        }
        String previous = LANGUAGE_OVERRIDE.get();
        LANGUAGE_OVERRIDE.set(normalized);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                LANGUAGE_OVERRIDE.remove();
            } else {
                LANGUAGE_OVERRIDE.set(previous);
            }
        }
    }

    public void runWithLanguage(String language, Runnable action) {
        withLanguage(language, () -> {
            action.run();
            return null;
        });
    }

    public String normalizeLanguage(String language) {
        String normalized = StringUtils.trimToEmpty(language).toLowerCase();
        if ("en".equals(normalized) || "zh".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public String text(String zh, String en) {
        return isEnglish() ? en : zh;
    }

    public String botNameForUS() {
        return text("美股分析专家", "US Stock Analyst");
    }

    public String botNameForA() {
        return text("A股分析专家", "Stock expert");
    }

    public String botNameForHK() {
        return text("港股分析专家", "Hong Kong Stock Analyst");
    }

    public String marketStateLabel(MarketState state) {
        if (state == null) {
            return text("中性", "Neutral");
        }
        return text(state.getLabel(), state.getEnglishLabel());
    }

    public String snapshotHealthLabel(MarketSnapshotHealth snapshotHealth) {
        if (snapshotHealth == null) {
            return "--";
        }
        return text(snapshotHealth.getLabel(), snapshotHealth.getEnglishLabel());
    }

    public String signalSideLabel(String signalSide) {
        if ("利空".equals(signalSide)) {
            return text("利空", "Bearish");
        }
        if ("利多".equals(signalSide)) {
            return text("利多", "Bullish");
        }
        return text("中性", "Neutral");
    }

    public String positionLabel(String positionLabel) {
        if ("领军核心".equals(positionLabel)) {
            return text("领军核心", "Leading Core");
        }
        if ("高弹性跟风".equals(positionLabel)) {
            return text("高弹性跟风", "High-Beta Follower");
        }
        if ("观察名单".equals(positionLabel)) {
            return text("观察名单", "Watchlist");
        }
        return StringUtils.defaultString(positionLabel);
    }

    public String eventType(String eventType) {
        if (!isEnglish()) {
            return StringUtils.defaultString(eventType);
        }
        return EVENT_TYPE_EN.getOrDefault(StringUtils.defaultString(eventType), StringUtils.defaultString(eventType));
    }

    public String postCloseDigestSummary(String eventType, String signalSide, int signalScore, int frequency) {
        if (!isEnglish()) {
            return "原始公告标题详见上方。";
        }

        String side = signalSideLabel(signalSide).toLowerCase();
        String eventLabel = StringUtils.defaultIfBlank(eventType(eventType), "notice");
        String urgency;
        if (signalScore >= 110) {
            urgency = "This is a main-theme-tier " + side + " signal";
        } else if (signalScore >= 80) {
            urgency = "This is a high-priority " + side + " signal";
        } else if (signalScore >= 60) {
            urgency = "This is a marginal " + side + " signal";
        } else {
            urgency = "This is a routine " + side + " signal";
        }

        String noticeLoad;
        if (frequency >= 4) {
            noticeLoad = "supported by dense same-window notice flow";
        } else if (frequency >= 2) {
            noticeLoad = "supported by repeated same-window notice flow";
        } else {
            noticeLoad = "based on a single tracked notice";
        }

        return urgency + " around " + eventLabel + ", " + noticeLoad + ".";
    }

    public String activityLevel(int frequency) {
        if (frequency >= 25) {
            return text("极度活跃", "Extremely Active");
        }
        if (frequency >= 15) {
            return text("高度活跃", "Highly Active");
        }
        if (frequency >= 10) {
            return text("中度活跃", "Moderately Active");
        }
        return text("轻度活跃", "Light Activity");
    }

    public String surgeDescription(int frequency) {
        if (frequency >= 25) {
            return text("较昨日激增 400%+", "roughly 400%+ above yesterday");
        }
        if (frequency >= 15) {
            return text("较昨日激增 200%+", "roughly 200%+ above yesterday");
        }
        if (frequency >= 10) {
            return text("较昨日显著上升", "materially above yesterday");
        }
        return text("活跃度一般", "normal activity");
    }

    public String signalLevel(int signalScore) {
        if (signalScore >= 110) {
            return text("主线级", "Main-Theme Tier");
        }
        if (signalScore >= 80) {
            return text("高优先级", "High Priority");
        }
        if (signalScore >= 60) {
            return text("边际催化", "Marginal Catalyst");
        }
        return text("常规事项", "Routine");
    }

    public String fusionLevel(int fusionScore) {
        if (fusionScore >= 130) {
            return text("强共振", "Strong Resonance");
        }
        if (fusionScore >= 100) {
            return text("高共振", "High Resonance");
        }
        if (fusionScore >= 80) {
            return text("弱共振", "Mild Resonance");
        }
        return text("观察", "Watch");
    }

    public String translatePositionReason(String reason) {
        if (!isEnglish() || StringUtils.isBlank(reason)) {
            return StringUtils.defaultString(reason);
        }
        return List.of(reason.split("；"))
                .stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(item -> POSITION_REASON_EN.getOrDefault(item, item))
                .collect(Collectors.joining("; "));
    }

    public String translateTradeHint(String tradeHint) {
        if (!isEnglish() || StringUtils.isBlank(tradeHint)) {
            return StringUtils.defaultString(tradeHint);
        }
        return TRADE_HINT_EN.getOrDefault(tradeHint, tradeHint);
    }

    public String translateRelationReason(String relationReason) {
        if (!isEnglish() || StringUtils.isBlank(relationReason)) {
            return StringUtils.defaultString(relationReason);
        }
        if (relationReason.startsWith("宏观主题映射命中：")) {
            return "Macro-theme mapping hit: " + relationReason.substring("宏观主题映射命中：".length());
        }
        return RELATION_REASON_EN.getOrDefault(relationReason, relationReason);
    }

    public String aiOutputInstruction() {
        if (!isEnglish()) {
            return "【语言要求】全文输出简体中文，标题、分栏、互动引导和免责声明都使用中文。";
        }
        return """
                [Language Requirement]
                - Output all user-facing markdown in English.
                - Output headings, labels, conclusions, interpretations, calls to action, disclaimers, and source-title paraphrases in English.
                - For A-stock reports, use English section headings such as "## Macro Themes", "## Resonance Picks", "## Opportunity Board", and "## Risk Board".
                - Stock codes, dates, numbers, and font color tags must remain unchanged.
                - Company names may remain unchanged, but the final user-facing prose must not contain Chinese narrative.
                - When source titles or source summaries are only available in Chinese, restate their meaning in English instead of copying raw Chinese text.
                """;
    }
}
