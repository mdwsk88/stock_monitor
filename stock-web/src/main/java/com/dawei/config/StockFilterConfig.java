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
}
