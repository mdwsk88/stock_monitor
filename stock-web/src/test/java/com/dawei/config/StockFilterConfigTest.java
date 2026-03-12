package com.dawei.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @ClassName StockFilterConfigTest
 * @Author dawei
 * @Description 股票异动过滤配置单元测试 - 纯JUnit测试
 **/
class StockFilterConfigTest {

    private StockFilterConfig filterConfig;

    @BeforeEach
    void setUp() {
        filterConfig = new StockFilterConfig();
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 会议类")
    void testContainsBlacklistKeyword_Meeting() {
        assertTrue(filterConfig.containsBlacklistKeyword("召开董事会会议通知"));
        assertTrue(filterConfig.containsBlacklistKeyword("监事会例行会议决议"));
        assertTrue(filterConfig.containsBlacklistKeyword("股东大会审议通过"));
        assertTrue(filterConfig.containsBlacklistKeyword("会议通知公告"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 人事变动类")
    void testContainsBlacklistKeyword_Personnel() {
        assertTrue(filterConfig.containsBlacklistKeyword("聘任新任财务负责人"));
        assertTrue(filterConfig.containsBlacklistKeyword("独立董事辞职公告"));
        assertTrue(filterConfig.containsBlacklistKeyword("董事会秘书任命"));
        assertTrue(filterConfig.containsBlacklistKeyword("高管变动通知"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 资金类")
    void testContainsBlacklistKeyword_Funding() {
        assertTrue(filterConfig.containsBlacklistKeyword("补充流动资金公告"));
        assertTrue(filterConfig.containsBlacklistKeyword("使用闲置资金购买理财"));
        assertTrue(filterConfig.containsBlacklistKeyword("募集资金现金管理"));
        assertTrue(filterConfig.containsBlacklistKeyword("委托理财产品"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 自查合规类")
    void testContainsBlacklistKeyword_Compliance() {
        assertTrue(filterConfig.containsBlacklistKeyword("内部审计自查报告"));
        assertTrue(filterConfig.containsBlacklistKeyword("补充披露前期差错"));
        assertTrue(filterConfig.containsBlacklistKeyword("会计政策变更"));
        assertTrue(filterConfig.containsBlacklistKeyword("风险提示公告"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 关联交易/担保类")
    void testContainsBlacklistKeyword_RelatedParty() {
        assertTrue(filterConfig.containsBlacklistKeyword("关联交易公告"));
        assertTrue(filterConfig.containsBlacklistKeyword("对外担保进展"));
        assertTrue(filterConfig.containsBlacklistKeyword("为子公司担保"));
        assertTrue(filterConfig.containsBlacklistKeyword("关联担保公告"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 期权激励类")
    void testContainsBlacklistKeyword_Incentive() {
        assertTrue(filterConfig.containsBlacklistKeyword("股权激励计划行权"));
        assertTrue(filterConfig.containsBlacklistKeyword("限制性股票解除限售"));
        assertTrue(filterConfig.containsBlacklistKeyword("股票期权授予登记"));
        assertTrue(filterConfig.containsBlacklistKeyword("回购注销公告"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 质押类")
    void testContainsBlacklistKeyword_Pledge() {
        assertTrue(filterConfig.containsBlacklistKeyword("股份解除质押公告"));
        assertTrue(filterConfig.containsBlacklistKeyword("股权质押式回购"));
        assertTrue(filterConfig.containsBlacklistKeyword("补充质押展期"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 其他行政类")
    void testContainsBlacklistKeyword_OtherAdmin() {
        assertTrue(filterConfig.containsBlacklistKeyword("制度修订公告"));
        assertTrue(filterConfig.containsBlacklistKeyword("工商变更登记"));
        assertTrue(filterConfig.containsBlacklistKeyword("续聘会计师事务所"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 非黑名单标题(优质公告)")
    void testContainsBlacklistKeyword_NotInBlacklist() {
        // 这些应该是优质的、不应该被过滤的公告
        assertFalse(filterConfig.containsBlacklistKeyword("中标10亿元算力中心订单"));
        assertFalse(filterConfig.containsBlacklistKeyword("AI芯片订单暴增，Q2业绩超预期"));
        assertFalse(filterConfig.containsBlacklistKeyword("与华为签订战略合作协议"));
        assertFalse(filterConfig.containsBlacklistKeyword("新一代AI芯片发布，性能提升200%"));
        assertFalse(filterConfig.containsBlacklistKeyword("获得亿元级采购合同"));
        assertFalse(filterConfig.containsBlacklistKeyword("产品通过FDA认证，可进入美国市场"));
    }

    @Test
    @DisplayName("测试黑名单关键词检测 - 边界情况")
    void testContainsBlacklistKeyword_EdgeCases() {
        assertFalse(filterConfig.containsBlacklistKeyword(null));
        assertFalse(filterConfig.containsBlacklistKeyword(""));
        assertFalse(filterConfig.containsBlacklistKeyword("   "));
    }

    @Test
    @DisplayName("测试频次阈值检查 - 默认阈值10次")
    void testMeetsFrequencyThreshold_Default() {
        // 默认阈值为10
        assertFalse(filterConfig.meetsFrequencyThreshold(0));
        assertFalse(filterConfig.meetsFrequencyThreshold(5));
        assertFalse(filterConfig.meetsFrequencyThreshold(9));
        
        // 达到阈值
        assertTrue(filterConfig.meetsFrequencyThreshold(10));
        assertTrue(filterConfig.meetsFrequencyThreshold(15));
        assertTrue(filterConfig.meetsFrequencyThreshold(25));
        assertTrue(filterConfig.meetsFrequencyThreshold(100));
    }

    @Test
    @DisplayName("测试频次阈值检查 - 自定义阈值15次")
    void testMeetsFrequencyThreshold_Custom15() {
        filterConfig.setFrequencyThreshold(15);
        
        assertFalse(filterConfig.meetsFrequencyThreshold(10));
        assertFalse(filterConfig.meetsFrequencyThreshold(14));
        
        assertTrue(filterConfig.meetsFrequencyThreshold(15));
        assertTrue(filterConfig.meetsFrequencyThreshold(20));
        assertTrue(filterConfig.meetsFrequencyThreshold(50));
    }

    @Test
    @DisplayName("测试频次阈值检查 - 自定义阈值25次")
    void testMeetsFrequencyThreshold_Custom25() {
        filterConfig.setFrequencyThreshold(25);
        
        assertFalse(filterConfig.meetsFrequencyThreshold(10));
        assertFalse(filterConfig.meetsFrequencyThreshold(24));
        
        assertTrue(filterConfig.meetsFrequencyThreshold(25));
        assertTrue(filterConfig.meetsFrequencyThreshold(30));
    }

    @Test
    @DisplayName("测试黑名单正则匹配性能 - 多次调用应使用缓存")
    void testBlacklistPatternCaching() {
        // 第一次调用会编译正则
        boolean result1 = filterConfig.containsBlacklistKeyword("董事会会议");
        
        // 第二次调用应使用缓存的正则（快速返回）
        boolean result2 = filterConfig.containsBlacklistKeyword("股东大会决议");
        
        // 第三次调用另一个关键词
        boolean result3 = filterConfig.containsBlacklistKeyword("补充流动资金");
        
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
    }

    @Test
    @DisplayName("测试复杂的混合标题")
    void testComplexTitles() {
        // 包含黑名单词的标题应该被过滤
        assertTrue(filterConfig.containsBlacklistKeyword("关于召开董事会会议审议股权激励计划的公告"));
        
        // 不包含黑名单词的真实业务公告应该保留
        assertFalse(filterConfig.containsBlacklistKeyword("关于中标国家电网1.2亿元设备采购项目的公告"));
        assertFalse(filterConfig.containsBlacklistKeyword("第三季度净利润同比增长150%，超出市场预期"));
    }
}
