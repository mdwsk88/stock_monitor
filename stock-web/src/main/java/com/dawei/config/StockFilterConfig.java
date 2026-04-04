package com.dawei.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @ClassName StockFilterConfig
 * @Author dawei
 * @Version 1.0
 * @Description 股票异动过滤配置 - 用于剔除"伪异动"噪音
 **/
@Data
@Component
@ConfigurationProperties(prefix = "stock.filter")
public class StockFilterConfig {

    /**
     * 异动频次阈值 - 低于此值的将被过滤
     * 默认：10次（宁缺毋滥原则）
     */
    private int frequencyThreshold = 10;

    /**
     * A股实时推送最低信号分
     */
    private int aRealtimeSignalThreshold = 70;

    /**
     * A股盘中机会预警最低信号分
     */
    private int aRealtimeOpportunityThreshold = 85;

    /**
     * A股盘中风险预警最低信号分
     */
    private int aRealtimeRiskThreshold = 88;

    /**
     * 防守态下的A股盘中风险预警最低信号分
     */
    private int aRealtimeRiskThresholdDefensive = 70;

    /**
     * A股盘中核弹级预警最低信号分
     */
    private int aRealtimeCriticalThreshold = 92;

    /**
     * 进攻态下的A股盘中机会预警最低信号分
     */
    private int aRealtimeOpportunityThresholdRiskOn = 78;

    /**
     * 高潮态下的A股盘中机会预警最低信号分
     */
    private int aRealtimeOpportunityThresholdOverheat = 80;

    /**
     * A股实时预警去重冷却时间（分钟）
     */
    private int aRealtimePushCooldownMinutes = 120;

    /**
     * A股实时预警宏观上下文回看窗口（小时）
     */
    private int aRealtimeContextHours = 24;

    /**
     * 市场状态快照刷新间隔（分钟）
     */
    private int marketSnapshotRefreshMinutes = 5;

    /**
     * 市场快照抓取重试次数
     */
    private int marketSnapshotRetryAttempts = 3;

    /**
     * 市场快照抓取重试退避（毫秒）
     */
    private long marketSnapshotRetryBackoffMillis = 150L;

    /**
     * 自动刷新遇到抓取失败后的退避时长（秒）
     */
    private int marketSnapshotFailureCooldownSeconds = 45;

    /**
     * 快照判定为失联前允许的连续失败次数
     */
    private int marketSnapshotDisconnectFailureThreshold = 3;

    /**
     * 东方财富市场宽度抓取上限
     */
    private int marketBreadthFetchLimit = 6000;

    /**
     * 市场宽度代理样本池大小
     */
    private int marketBreadthSampleSize = 800;

    /**
     * 市场宽度代理样本池回看天数
     */
    private int marketBreadthSampleLookbackDays = 180;

    /**
     * 腾讯批量行情单次请求股票数
     */
    private int marketQuoteBatchSize = 60;

    /**
     * 样本宽度健康告警阈值：低于该样本数时，认为宽度代理置信度不足
     */
    private int marketBreadthSampleWarnThreshold = 200;

    /**
     * 防守态阈值：最弱核心指数跌幅
     */
    private double marketDefensiveIndexDropThreshold = -1.5d;

    /**
     * 进攻态阈值：最强核心指数涨幅
     */
    private double marketRiskOnIndexRiseThreshold = 1.2d;

    /**
     * 高潮态阈值：最强核心指数涨幅
     */
    private double marketOverheatIndexRiseThreshold = 2.4d;

    /**
     * 防守态阈值：上涨家数占比
     */
    private double marketDefensiveBreadthThreshold = 0.35d;

    /**
     * 进攻态阈值：上涨家数占比
     */
    private double marketRiskOnBreadthThreshold = 0.60d;

    /**
     * 高潮态阈值：上涨家数占比
     */
    private double marketOverheatBreadthThreshold = 0.72d;

    /**
     * 防守态阈值：近似跌停家数
     */
    private int marketDefensiveLimitDownThreshold = 80;

    /**
     * 高潮态阈值：近似涨停家数
     */
    private int marketOverheatLimitUpThreshold = 80;

    /**
     * 市场脉冲去重冷却时间（分钟）
     */
    private int marketPulseCooldownMinutes = 20;

    /**
     * 宏观级/情绪级同向告警家族冷却时间（分钟）
     */
    private int marketAlertFamilyCooldownMinutes = 120;

    /**
     * 市场状态最短驻留时间（分钟），用于抑制短时间内牛熊来回切换
     */
    private int marketStateFamilyMinDwellMinutes = 60;

    /**
     * 防守态确认窗口（分钟）
     */
    private int marketStateDefensiveConfirmMinutes = 15;

    /**
     * 中性态确认窗口（分钟）
     */
    private int marketStateNeutralConfirmMinutes = 20;

    /**
     * 进攻态确认窗口（分钟）
     */
    private int marketStateRiskOnConfirmMinutes = 30;

    /**
     * 高潮态确认窗口（分钟）
     */
    private int marketStateOverheatConfirmMinutes = 120;

    /**
     * 状态确认所需的最小样本数
     */
    private int marketStateConfirmMinObservations = 3;

    /**
     * 状态确认所需的窗口命中比例
     */
    private double marketStateConfirmRatio = 0.7d;

    /**
     * 极端风险强制切换阈值：任一核心指数跌幅
     */
    private double marketPanicIndexDropThreshold = -3.0d;

    /**
     * 极端风险强制切换阈值：近似跌停家数
     */
    private int marketPanicLimitDownThreshold = 150;

    /**
     * 极端风险强制切换阈值：上涨家数占比
     */
    private double marketPanicBreadthThreshold = 0.25d;

    /**
     * A股实时链路健康巡检窗口（分钟）
     */
    private int realtimeHealthWindowMinutes = 120;

    /**
     * 触发健康告警的高分公告数量阈值
     */
    private int realtimeHealthHighSignalCountThreshold = 8;

    /**
     * 触发健康告警的硬风险公告数量阈值
     */
    private int realtimeHealthHardRiskCountThreshold = 2;

    /**
     * 触发健康告警的决策日志数量阈值
     */
    private int realtimeHealthDecisionCountThreshold = 8;

    /**
     * 健康告警的跳过占比阈值
     */
    private double realtimeHealthSkippedRatioThreshold = 0.9d;

    /**
     * 健康告警的发送失败阈值
     */
    private int realtimeHealthFailureCountThreshold = 2;

    /**
     * 宏观风险健康告警阈值
     */
    private int realtimeHealthMacroRiskCountThreshold = 2;

    /**
     * 宏观机会健康告警阈值
     */
    private int realtimeHealthMacroOpportunityCountThreshold = 3;

    /**
     * 实时链路健康告警冷却时间（分钟）
     */
    private int realtimeHealthCooldownMinutes = 30;

    /**
     * A股榜单入围最低信号分
     */
    private int aRankingSignalThreshold = 60;

    /**
     * A股抓取时最多回扫页数
     */
    private int aFetchPageCount = 5;

    /**
     * A股批量公告聚类时间窗口（分钟）
     */
    private int aClusterWindowMinutes = 15;

    /**
     * 宏观影子榜最低信号分
     */
    private int macroShadowSignalThreshold = 75;

    /**
     * 每个宏观源单次抓取的记录上限
     */
    private int macroFetchLimitPerSource = 20;

    /**
     * 宏观主题聚类窗口（分钟）
     */
    private int macroClusterWindowMinutes = 120;

    /**
     * 宏观实时风险推送阈值
     */
    private int macroRealtimeRiskThreshold = 82;

    /**
     * 防守态下的宏观实时风险推送阈值
     */
    private int macroRealtimeRiskThresholdDefensive = 78;

    /**
     * 宏观实时机会推送阈值
     */
    private int macroRealtimeOpportunityThreshold = 92;

    /**
     * 进攻态下的宏观实时机会推送阈值
     */
    private int macroRealtimeOpportunityThresholdRiskOn = 86;

    /**
     * 高潮态下的宏观实时机会推送阈值
     */
    private int macroRealtimeOpportunityThresholdOverheat = 90;

    /**
     * 宏观实时推送冷却时间（分钟）
     */
    private int macroRealtimePushCooldownMinutes = 90;

    /**
     * 宏观实时回扫窗口（分钟）
     */
    private int macroRealtimeScanWindowMinutes = 60;

    /**
     * 黑名单关键词列表 - 用于过滤"行政流水账"类噪音公告
     */
    private List<String> blacklistKeywords = Arrays.asList(
            // 会议类
            "例会", "例行会议", "董事会", "监事会", "股东大会", "会议决议",
            "召开会议", "会议通知", "会后公告", "会议审议",
            
            // 人事变动类
            "人事变动", "任命", "聘任", "解聘", "辞职", "离职", "新任",
            "董事任命", "高管变动", "独立董事", "财务负责人", "董事会秘书",
            
            // 资金类（日常运营）
            "补充流动资金", "流动资金", "募集资金", "使用闲置资金",
            "现金管理", "购买理财", "结构性存款", "委托理财",
            
            // 自查/合规类
            "自查", "内部审计", "合规检查", "风险提示", "补充披露",
            "更正公告", "补充公告", "前期差错", "会计政策",
            
            // 关联交易/担保类
            "关联交易", "关联担保", "对外担保", "为子公司担保",
            "互保", "反担保", "担保进展",
            
            // 期权激励类
            "期权激励", "股权激励", "限制性股票", "股票期权", "授予登记",
            "解除限售", "回购注销", "激励计划", "行权",
            
            // 质押类（常规操作）
            "股份解除质押", "股权质押", "解除质押", "质押式回购",
            "补充质押", "质押展期",
            
            // 其他常规行政公告
            "制度修订", "章程修订", "规则修改", "制度完善",
            "工商变更", "注册地址变更", "经营范围变更", "更名",
            "续聘会计师", "聘请律师", "持续督导"
    );

    /**
     * 东方财富公告标签级别的强噪音类型
     */
    private List<String> trashTagKeywords = Arrays.asList(
            "董事会决议", "监事会决议", "股东大会决议", "召开会议", "会议通知",
            "募集资金使用情况报告", "募集资金使用进展情况", "募集资金补充流动资金",
            "投资理财", "委托（受托）事项", "现金管理", "股份质押、冻结",
            "制度修订", "章程修订", "工商登记", "工商变更", "续聘会计师",
            "会计师事务所专项意见", "审计报告", "内部控制", "非经营性资金占用"
    );

    /**
     * 保留入库但默认降权的公告标签
     */
    private List<String> grayTagKeywords = Arrays.asList(
            "风险提示性公告", "股票交易异常波动", "监管工作函回复公告",
            "问询函回复", "重组进展公告", "签订协议", "投资设立公司", "其他"
    );

    /**
     * 预编译的正则模式 - 用于快速匹配
     */
    private volatile Pattern blacklistPattern;

    /**
     * 检查标题是否包含黑名单关键词
     * @param title 新闻/公告标题
     * @return true - 包含黑名单词（应被过滤）, false - 不包含（应保留）
     */
    public boolean containsBlacklistKeyword(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        // 使用正则进行一次性匹配（性能优化）
        if (blacklistPattern == null) {
            synchronized (this) {
                if (blacklistPattern == null) {
                    String regex = blacklistKeywords.stream()
                            .map(Pattern::quote)
                            .reduce((a, b) -> a + "|" + b)
                            .orElse("");
                    blacklistPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                }
            }
        }
        
        return blacklistPattern.matcher(title).find();
    }

    /**
     * 检查频次是否达到阈值
     * @param frequency 异动频次
     * @return true - 达到阈值, false - 未达到
     */
    public boolean meetsFrequencyThreshold(int frequency) {
        return frequency >= frequencyThreshold;
    }

    public boolean containsAnyTagKeyword(String tagText, List<String> keywords) {
        if (tagText == null || tagText.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream().anyMatch(tagText::contains);
    }
}
