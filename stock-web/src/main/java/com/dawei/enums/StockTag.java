package com.dawei.enums;

public enum StockTag {

    AI("AI", "🤖 AI人工智能"),
    PRIVATE_PLACEMENT("private placement", "💰 私募"),
    MANAGEMENT("management", "👔 公司管理团队"),
    HIGH_SHORT("high short", "📉 短期"),
    ACQUISITION("acquisition", "🏢 收购"),
    MERGER("merger", "🤝 合并"),
    LOW_FLOAT("low float", "⚖️ 低浮动"),
    PENNY_STOCK("penny stock", "💵 低价股"),
    EARNINGS("earnings", "📊 财报盈利"),
    REVENUE("revenue", "💹 收入"),
    GUIDANCE("guidance", "📝 业绩预期"),
    DIVIDEND("dividend", "💸 分红"),
    BUYBACK("buyback", "🔄 回购"),
    UPGRADE("upgrade", "⬆️ 上调评级"),
    DOWNGRADE("downgrade", "⬇️ 下调评级"),
    FDA("FDA", "🏥 美国食品药品管理局"),
    PARTNERSHIP("partnership", "🤝 战略合作"),
    FINANCING("financing", "💳 融资"),
    BANKRUPTCY("bankruptcy", "💀 破产"),
    LAWSUIT("lawsuit", "⚖️ 诉讼"),
    INSIDER_TRADING("insider trading", "🔒 内幕交易"),
    VOLATILITY("volatility", "🌪️ 波动性"),
    SENTIMENT("sentiment", "📈 市场情绪"),
    IMPACT("impact", "💥 影响"),
    IPO("IPO", "🚀 首次公开募股"),
    ETF("ETF", "📊 交易型基金"),
    CONFERENCES("conferences", "🏛️ 投资者会议"),
    CLINICAL_TRIAL("clinical trial", "🧪 临床试验"),
    STOCK_SPLIT("stock split", "✂️ 股票拆分"),
    OFFERING("offering", "📢 发行");

    private final String key;
    private final String description;

    StockTag(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public static StockTag fromKey(String key) {
        for (StockTag tag : values()) {
            if (tag.key.equalsIgnoreCase(key)) {
                return tag;
            }
        }
        return null;
    }

    public static String getTagValue(String key) {
        for (StockTag tag : values()) {
            if (tag.key.equalsIgnoreCase(key)) {
                return tag.description;
            }
        }
        return null;
    }

}
