package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 宏观新闻规则打分服务：
 * 1. 识别宏观/政策/产业级快讯
 * 2. 过滤明显的个股噪音
 * 3. 输出统一的主题、事件类型、方向和评分
 */
@Component
public class MacroNewsSignalService {

    private static final DateTimeFormatter CLUSTER_BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Pattern TITLE_CLEAN_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]");
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\b\\d{6}\\.(sh|sz|bj)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> CENTRAL_POLICY_KEYWORDS = List.of(
            "中共中央", "国务院", "国务院办公厅", "中共中央办公厅", "国办", "部际联席会议"
    );
    private static final List<String> POLICY_DOCUMENT_KEYWORDS = List.of(
            "意见", "方案", "措施", "通知", "指引", "规划", "行动计划", "实施方案", "工作方案"
    );
    private static final List<String> MONETARY_POLICY_KEYWORDS = List.of(
            "中国人民银行", "人民银行", "央行", "逆回购", "MLF", "LPR", "SLF", "公开市场业务",
            "降准", "降息", "准备金", "净投放", "净回笼"
    );
    private static final List<String> EASING_KEYWORDS = List.of(
            "降准", "降息", "下调", "净投放", "投放", "支持", "加大金融支持", "便利互换", "再贷款"
    );
    private static final List<String> TIGHTENING_KEYWORDS = List.of(
            "净回笼", "上调", "提高", "收紧", "从严", "处罚", "严查", "打击"
    );
    private static final List<String> CSRC_KEYWORDS = List.of(
            "中国证监会", "证监会", "上交所", "深交所", "北交所", "资本市场", "并购重组", "再融资",
            "IPO", "交易监管", "信息披露"
    );
    private static final List<String> STATS_KEYWORDS = List.of(
            "国家统计局", "统计局", "CPI", "PPI", "PMI", "GDP", "工业增加值", "社会消费品零售总额",
            "社零", "固定资产投资", "失业率", "进出口", "房价", "70个大中城市"
    );
    private static final List<String> POLICY_INSTITUTION_KEYWORDS = List.of(
            "工信部", "发改委", "财政部", "商务部", "住建部", "国资委", "药监局", "金融监管总局"
    );
    private static final List<String> INDUSTRY_POLICY_KEYWORDS = List.of(
            "支持", "补贴", "限产", "提价", "涨价", "扩产", "减产", "招标", "投资日历"
    );
    private static final List<String> TRADE_FRICTION_KEYWORDS = List.of(
            "关税", "301调查", "贸易代表办公室", "贸易摩擦", "出口管制", "实体清单",
            "反倾销", "反补贴", "加征关税", "禁运", "制裁", "强迫劳动"
    );
    private static final List<String> GLOBAL_RISK_KEYWORDS = List.of(
            "霍尔木兹", "红海", "原油", "黄金", "天然气", "中东", "地缘",
            "战争", "冲突", "伊朗", "以色列", "油轮", "袭击"
    );
    private static final List<String> POLICY_SUPPORT_KEYWORDS = List.of(
            "支持", "补贴", "试点", "示范", "规划", "方案", "意见", "措施", "行动计划",
            "实施方案", "工作方案", "政策", "通知", "鼓励"
    );
    private static final List<String> PRICE_CATALYST_KEYWORDS = List.of(
            "涨价", "提价", "限产", "减产", "停产", "挺价"
    );
    private static final List<String> DEMAND_CATALYST_KEYWORDS = List.of(
            "订单", "中标", "招标", "扩产", "采购", "装机", "放量", "景气", "需求回暖", "产能"
    );
    private static final List<String> PRICE_UP_KEYWORDS = List.of(
            "上涨", "走高", "飙升", "拉升", "涨超", "上行"
    );
    private static final List<String> PRICE_DOWN_KEYWORDS = List.of(
            "下跌", "跌破", "回落", "走低", "跌超", "承压"
    );
    private static final List<String> QUICK_SOURCE_NOISE_KEYWORDS = List.of(
            "董事会", "监事会", "股东大会", "年报", "季报", "业绩说明会", "互动平台", "公告速递",
            "个股异动", "涨停", "跌停", "龙虎榜", "融资融券", "主力资金", "回购注销", "限售股",
            "证券：", "券商：", "研报", "分析师", "建议关注", "建议持续关注", "指出", "认为"
    );
    private static final List<String> QUICK_SOURCE_DIGEST_NOISE_KEYWORDS = List.of(
            "新闻精选", "要闻精选", "快讯精选", "盘前必读", "午间公告精选", "晚间公告精选",
            "午评", "收评", "早评", "盘中播报", "盘中速览"
    );
    private static final List<String> QUICK_SOURCE_MARKET_DIGEST_KEYWORDS = List.of(
            "商品期货", "主力合约多数", "早盘开盘", "早盘多数", "午盘多数", "夜盘多数"
    );
    private static final List<String> MACRO_PREFIX_KEYWORDS = List.of(
            "中共中央", "国务院", "国办", "央行", "人民银行", "中国人民银行", "证监会", "中国证监会",
            "工信部", "发改委", "财政部", "商务部", "统计局", "国家统计局", "国资委", "金融监管总局",
            "新华社", "海关总署", "住建部", "药监局", "交易所", "上交所", "深交所", "北交所"
    );
    private static final List<String> QUICK_SOURCE_STOCK_ENTITY_KEYWORDS = List.of(
            "公告称", "公司及", "股份有限公司", "董事长", "收到", "立案", "涉嫌", "停牌", "复牌",
            "异常波动", "风险提示", "终止上市", "收到问询函", "股份将被", "司法拍卖"
    );
    private static final List<String> MARKET_DATA_KEYWORDS = List.of(
            "融资余额", "融券余额", "两市融资余额", "两融余额", "北向资金", "南向资金",
            "成交额", "主力净流入", "主力净流出", "换手率", "资金净流入", "资金净流出"
    );
    private static final List<ThemeProfile> THEME_PROFILES = List.of(
            new ThemeProfile("国企改革", List.of("国企改革", "国资国企", "央企重组", "国企并购")),
            new ThemeProfile("并购重组", List.of("并购重组", "重大资产重组", "资产注入", "发行股份购买资产", "收购资产")),
            new ThemeProfile("金融", List.of("券商", "保险", "银行", "资本市场", "金融支持", "金融开放")),
            new ThemeProfile("稳增长", List.of("稳增长", "专项债", "基建投资", "重大项目", "设备更新", "城中村改造", "地产政策")),
            new ThemeProfile("低空经济", List.of("低空经济", "evtol", "飞行汽车", "无人机", "通航")),
            new ThemeProfile("算力", List.of("算力", "算力基础设施", "智算", "数据中心", "液冷", "服务器")),
            new ThemeProfile("人工智能", List.of("人工智能", "大模型", "aigc", "智能体")),
            new ThemeProfile("机器人", List.of("机器人", "人形机器人", "工业机器人", "机器狗")),
            new ThemeProfile("半导体", List.of("半导体", "芯片", "存储", "先进封装", "光刻", "晶圆")),
            new ThemeProfile("创新药", List.of("创新药", "adc", "car-t", "pd-1", "双抗", "减重药")),
            new ThemeProfile("稀土", List.of("稀土", "镨钕", "氧化镨钕", "轻稀土", "重稀土")),
            new ThemeProfile("光伏", List.of("光伏", "硅料", "硅片", "topcon", "bc电池")),
            new ThemeProfile("锂电", List.of("锂电", "电池级碳酸锂", "固态电池", "钠电", "锂矿")),
            new ThemeProfile("风电", List.of("风电", "海上风电", "风机")),
            new ThemeProfile("军工", List.of("军工", "国防装备", "导弹", "卫星互联网")),
            new ThemeProfile("原油", List.of("原油", "油价", "布伦特", "wti")),
            new ThemeProfile("黄金", List.of("黄金", "金价", "避险资产")),
            new ThemeProfile("天然气", List.of("天然气", "lng"))
    );

    private final StockFilterConfig filterConfig;

    public MacroNewsSignalService(StockFilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public boolean enrichEvent(MacroThemeEvent event, MacroNewsRaw raw) {
        MacroSignalDecision decision = evaluate(raw);
        if (decision == null) {
            return false;
        }
        event.setThemeName(decision.themeName());
        event.setEventType(decision.eventType());
        event.setSignalSide(decision.signalSide());
        event.setSignalScore(decision.signalScore());
        event.setImportanceLevel(decision.importanceLevel());
        event.setClusterKey(buildClusterKey(raw, decision));
        return true;
    }

    public boolean meetsShadowThreshold(int signalScore) {
        return signalScore >= filterConfig.getMacroShadowSignalThreshold();
    }

    public int getFetchLimitPerSource() {
        return Math.max(5, filterConfig.getMacroFetchLimitPerSource());
    }

    private MacroSignalDecision evaluate(MacroNewsRaw raw) {
        if (raw == null || StringUtils.isBlank(raw.getTitle())) {
            return null;
        }

        String title = normalize(raw.getTitle());
        String combined = buildCombinedText(raw);
        String specificTheme = detectSpecificTheme(combined).orElse(null);

        if (isQuickSource(raw) && !isRecognizedQuickMacro(combined, specificTheme)) {
            return null;
        }
        if (isQuickSource(raw) && containsAny(combined, QUICK_SOURCE_NOISE_KEYWORDS)) {
            return null;
        }
        if (isQuickSource(raw) && containsAny(title, QUICK_SOURCE_DIGEST_NOISE_KEYWORDS)) {
            return null;
        }
        if (isQuickSource(raw) && containsAny(combined, QUICK_SOURCE_MARKET_DIGEST_KEYWORDS)) {
            return null;
        }
        if (isQuickSource(raw) && isLikelyCompanySpecificQuick(raw, combined)) {
            return null;
        }

        if (containsCentralPolicy(title, combined)) {
            if (StringUtils.isNotBlank(specificTheme)) {
                return new MacroSignalDecision(specificTheme, "政策扶持", "利多", 115, 5);
            }
            return new MacroSignalDecision("国家政策", "政策发布", "利多", 112, 5);
        }
        if (containsAny(combined, MONETARY_POLICY_KEYWORDS)) {
            if (containsAny(combined, EASING_KEYWORDS)) {
                return new MacroSignalDecision("货币政策", "流动性宽松", "利多", 106, 5);
            }
            if (containsAny(combined, TIGHTENING_KEYWORDS)) {
                return new MacroSignalDecision("货币政策", "流动性收紧", "利空", 96, 4);
            }
            return new MacroSignalDecision("货币政策", "公开市场操作", "中性", 90, 4);
        }
        if (containsAny(combined, MARKET_DATA_KEYWORDS)) {
            return new MacroSignalDecision("市场资金面", "交易数据", "中性", 68, 2);
        }
        if (containsAny(combined, TRADE_FRICTION_KEYWORDS)) {
            return new MacroSignalDecision("外部风险", "贸易摩擦", "利空", 86, 4);
        }
        if (containsAny(combined, STATS_KEYWORDS)) {
            return new MacroSignalDecision("宏观数据", "数据发布", "中性", 88, 4);
        }
        if (StringUtils.isNotBlank(specificTheme)) {
            if (isCommodityTheme(specificTheme) && containsAny(combined, PRICE_UP_KEYWORDS)) {
                return new MacroSignalDecision(specificTheme, "价格催化", "利多", 84, 3);
            }
            if (isCommodityTheme(specificTheme) && containsAny(combined, PRICE_DOWN_KEYWORDS)) {
                return new MacroSignalDecision(specificTheme, "价格承压", "利空", 74, 2);
            }
            if (containsAny(combined, GLOBAL_RISK_KEYWORDS) && isRiskBeneficiaryTheme(specificTheme)) {
                return new MacroSignalDecision(specificTheme, "地缘催化", "利多", 82, 3);
            }
            if (containsAny(combined, POLICY_DOCUMENT_KEYWORDS)
                    || containsAny(combined, CENTRAL_POLICY_KEYWORDS)
                    || containsAny(combined, POLICY_INSTITUTION_KEYWORDS)) {
                return new MacroSignalDecision(specificTheme, "政策扶持", "利多", 90, 4);
            }
            if (containsAny(combined, PRICE_CATALYST_KEYWORDS)) {
                return new MacroSignalDecision(specificTheme, "价格催化", "利多", 86, 4);
            }
            if (containsAny(combined, DEMAND_CATALYST_KEYWORDS)) {
                return new MacroSignalDecision(specificTheme, "景气催化", "利多", 84, 3);
            }
            return new MacroSignalDecision(specificTheme, "产业观察", "中性", 78, 3);
        }
        if (containsAny(combined, CSRC_KEYWORDS)) {
            if (containsAny(combined, TIGHTENING_KEYWORDS)) {
                return new MacroSignalDecision("资本市场监管", "监管收紧", "利空", 98, 4);
            }
            return new MacroSignalDecision("资本市场监管", "监管政策", "利多", 95, 4);
        }
        if (containsAny(combined, POLICY_INSTITUTION_KEYWORDS) || containsAny(combined, INDUSTRY_POLICY_KEYWORDS)) {
            if (containsAny(combined, POLICY_INSTITUTION_KEYWORDS) || containsAny(combined, POLICY_SUPPORT_KEYWORDS)) {
                return new MacroSignalDecision("行业催化", "政策扶持", "利多", 82, 3);
            }
            return new MacroSignalDecision("行业催化", "产业观察", "中性", 76, 3);
        }
        if (containsAny(combined, GLOBAL_RISK_KEYWORDS)) {
            return new MacroSignalDecision("全球风险", "地缘与商品冲击", "中性", 72, 3);
        }

        if (isOfficialSource(raw)) {
            return new MacroSignalDecision("官方发布", "政策动态", "中性", 70, 3);
        }
        return null;
    }

    private Optional<String> detectSpecificTheme(String combined) {
        return THEME_PROFILES.stream()
                .filter(profile -> containsAny(combined, profile.keywords()))
                .map(ThemeProfile::themeName)
                .findFirst();
    }

    private boolean isRecognizedQuickMacro(String combined, String specificTheme) {
        return StringUtils.isNotBlank(specificTheme)
                || containsAny(combined, CENTRAL_POLICY_KEYWORDS)
                || containsAny(combined, POLICY_DOCUMENT_KEYWORDS)
                || containsAny(combined, MONETARY_POLICY_KEYWORDS)
                || containsAny(combined, MARKET_DATA_KEYWORDS)
                || containsAny(combined, CSRC_KEYWORDS)
                || containsAny(combined, TRADE_FRICTION_KEYWORDS)
                || containsAny(combined, STATS_KEYWORDS)
                || containsAny(combined, POLICY_INSTITUTION_KEYWORDS)
                || containsAny(combined, INDUSTRY_POLICY_KEYWORDS)
                || containsAny(combined, GLOBAL_RISK_KEYWORDS);
    }

    private boolean isLikelyCompanySpecificQuick(MacroNewsRaw raw, String combined) {
        String title = StringUtils.defaultString(raw.getTitle());
        if (STOCK_CODE_PATTERN.matcher(combined).find()) {
            return true;
        }
        if (containsAny(combined, QUICK_SOURCE_STOCK_ENTITY_KEYWORDS)) {
            return true;
        }
        int delimiterIndex = Math.max(title.indexOf('：'), title.indexOf(':'));
        if (delimiterIndex <= 0) {
            return false;
        }
        String prefix = title.substring(0, delimiterIndex).trim();
        if (prefix.isEmpty()) {
            return false;
        }
        if (containsAny(prefix.toLowerCase(Locale.ROOT), MACRO_PREFIX_KEYWORDS)) {
            return false;
        }
        return prefix.length() <= 16;
    }

    private boolean isRiskBeneficiaryTheme(String themeName) {
        return List.of("军工", "黄金", "原油", "天然气").contains(themeName);
    }

    private boolean isCommodityTheme(String themeName) {
        return List.of("黄金", "原油", "天然气", "稀土").contains(themeName);
    }

    private boolean containsCentralPolicy(String title, String combined) {
        return containsAny(combined, CENTRAL_POLICY_KEYWORDS)
                && containsAny(combined, POLICY_DOCUMENT_KEYWORDS)
                && !title.contains("考试录用")
                && !title.contains("名录");
    }

    private boolean isQuickSource(MacroNewsRaw raw) {
        return "QUICK".equalsIgnoreCase(raw.getSourceType());
    }

    private boolean isOfficialSource(MacroNewsRaw raw) {
        return "OFFICIAL".equalsIgnoreCase(raw.getSourceType());
    }

    private String buildCombinedText(MacroNewsRaw raw) {
        return normalize(raw.getTitle()) + " " + normalize(raw.getContent()) + " " + normalize(raw.getSourceTags());
    }

    private String normalize(String value) {
        return StringUtils.defaultString(value)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StringUtils.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return keywords.stream().map(k -> k.toLowerCase(Locale.ROOT)).anyMatch(normalized::contains);
    }

    private String buildClusterKey(MacroNewsRaw raw, MacroSignalDecision decision) {
        LocalDateTime pubDate = raw.getPubDate() != null ? raw.getPubDate() : LocalDateTime.now();
        int window = Math.max(15, filterConfig.getMacroClusterWindowMinutes());
        int bucketMinute = (pubDate.getMinute() / window) * window;
        LocalDateTime bucket = pubDate.withMinute(bucketMinute).withSecond(0).withNano(0);
        return decision.themeName() + "|" + decision.eventType() + "|" + bucket.format(CLUSTER_BUCKET_FORMATTER)
                + "|" + buildTitleStem(raw.getTitle());
    }

    private String buildTitleStem(String title) {
        String cleaned = TITLE_CLEAN_PATTERN.matcher(StringUtils.defaultString(title)).replaceAll("");
        if (cleaned.length() <= 18) {
            return cleaned;
        }
        return cleaned.substring(0, 18);
    }

    private record MacroSignalDecision(String themeName,
                                       String eventType,
                                       String signalSide,
                                       int signalScore,
                                       int importanceLevel) {
    }

    private record ThemeProfile(String themeName, List<String> keywords) {
    }
}
