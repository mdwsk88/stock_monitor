package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MacroNewsSignalServiceTest {

    private final MacroNewsSignalService macroNewsSignalService = new MacroNewsSignalService(new StockFilterConfig());

    @Test
    void testGovernmentPolicyGetsHighScore() {
        MacroNewsRaw raw = raw("中国政府网", "OFFICIAL",
                "国务院办公厅关于促进低空经济高质量发展的意见",
                "国务院办公厅印发意见，提出若干措施支持低空经济发展。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("低空经济", event.getThemeName());
        assertEquals("政策扶持", event.getEventType());
        assertEquals("利多", event.getSignalSide());
        assertTrue(event.getSignalScore() >= 110);
    }

    @Test
    void testPbcOpenMarketRecognizedAsMacroEvent() {
        MacroNewsRaw raw = raw("中国人民银行", "OFFICIAL",
                "中国人民银行开展5000亿元逆回购操作",
                "人民银行开展逆回购操作并实现净投放，维护流动性合理充裕。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("货币政策", event.getThemeName());
        assertEquals("利多", event.getSignalSide());
        assertTrue(event.getSignalScore() >= 100);
    }

    @Test
    void testStatsReleaseRecognizedAsMacroData() {
        MacroNewsRaw raw = raw("国家统计局", "OFFICIAL",
                "2026年2月份居民消费价格同比上涨1.3%",
                "国家统计局发布2月份CPI数据。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("宏观数据", event.getThemeName());
        assertEquals("数据发布", event.getEventType());
        assertEquals("中性", event.getSignalSide());
    }

    @Test
    void testQuickSourceStockNoiseIsFiltered() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "某公司董事会审议通过年报议案",
                "快讯：某公司董事会审议通过2025年年报。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertFalse(macroNewsSignalService.enrichEvent(event, raw));
    }

    @Test
    void testQuickSourceIndustryCatalystCanPass() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "工信部：支持算力基础设施建设并推动人工智能产业发展",
                "快讯：工信部表示将加大支持力度，推动算力和人工智能产业发展。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("算力", event.getThemeName());
        assertEquals("政策扶持", event.getEventType());
        assertTrue(event.getSignalScore() >= 90);
    }

    @Test
    void testQuickSourceStateOwnedReformIsRecognizedAsSpecificTheme() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "新一轮国资国企改革重点明晰 有望催生A股并购新机遇",
                "国务院国资委将进一步优化国有经济布局，市场关注央企重组与资产注入。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("国企改革", event.getThemeName());
        assertEquals("政策扶持", event.getEventType());
        assertEquals("利多", event.getSignalSide());
    }

    @Test
    void testQuickSourceSteadyGrowthThemeIsRecognized() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "发改委表示将加快重大项目建设并扩大专项债使用",
                "稳增长政策继续加码，重大项目、专项债和设备更新有望提速。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("稳增长", event.getThemeName());
        assertEquals("政策扶持", event.getEventType());
        assertEquals("利多", event.getSignalSide());
    }

    @Test
    void testQuickSourceFinancialThemeIsRecognized() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "金融监管总局发文支持保险资金加大权益投资力度",
                "保险资金、券商和银行等金融机构有望受益于资本市场支持政策。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("金融", event.getThemeName());
        assertEquals("政策扶持", event.getEventType());
        assertEquals("利多", event.getSignalSide());
    }

    @Test
    void testQuickSourceGeopoliticalShockCanMapToBeneficiaryTheme() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "伊朗局势升级推动国际油价走强",
                "快讯：中东冲突升级，国际原油与黄金价格同步走高。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("原油", event.getThemeName());
        assertEquals("价格催化", event.getEventType());
        assertEquals("利多", event.getSignalSide());
    }

    @Test
    void testQuickSourceBrokerViewIsFiltered() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "华泰证券：建议持续关注推理侧算力与Agent应用机会",
                "华泰证券指出，建议持续关注推理侧算力、平台型基础设施以及Agent应用机会。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertFalse(macroNewsSignalService.enrichEvent(event, raw));
    }

    @Test
    void testQuickSourceSingleStockNewsIsFiltered() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "ST柯利达：公司及董事长顾益明涉嫌信息披露违法违规被证监会立案",
                "ST柯利达(603828.SH)公告称，公司及董事长顾益明收到中国证监会下发的《立案告知书》。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertFalse(macroNewsSignalService.enrichEvent(event, raw));
    }

    @Test
    void testQuickSourceDigestHeadlineIsFiltered() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "3月16日午间新闻精选",
                "国家统计局发布多项经济数据，市场整理核心观点。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertFalse(macroNewsSignalService.enrichEvent(event, raw));
    }

    @Test
    void testQuickSourceMarketDataIsDowngradedBelowShadowThreshold() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "两市融资余额减少126.08亿元",
                "截至3月15日，上交所融资余额减少，深交所融资余额同步回落。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("市场资金面", event.getThemeName());
        assertEquals("交易数据", event.getEventType());
        assertEquals("中性", event.getSignalSide());
        assertTrue(event.getSignalScore() < 75);
    }

    @Test
    void testQuickSourceCommodityMarketDigestIsFiltered() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "国内商品期货早盘开盘 主力合约多数下跌",
                "原油、焦煤等主力合约多数下跌。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertFalse(macroNewsSignalService.enrichEvent(event, raw));
    }

    @Test
    void testCommodityPriceDropDoesNotEnterShadowLevelBullishTheme() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "现货黄金向下跌破5000美元",
                "现货黄金向下跌破5000美元，日内下跌0.55%。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("黄金", event.getThemeName());
        assertEquals("价格承压", event.getEventType());
        assertEquals("利空", event.getSignalSide());
        assertTrue(event.getSignalScore() < 75);
    }

    @Test
    void testTradeFrictionQuickSourceBecomesExternalRisk() {
        MacroNewsRaw raw = raw("东方财富快讯", "QUICK",
                "商务部新闻发言人就美贸易代表办公室宣布对包括中国在内的60个经济体发起301调查答记者问",
                "美贸易代表办公室发起301调查，中方表示坚决反对将经贸问题政治化。");
        MacroThemeEvent event = new MacroThemeEvent();

        assertTrue(macroNewsSignalService.enrichEvent(event, raw));
        assertEquals("外部风险", event.getThemeName());
        assertEquals("贸易摩擦", event.getEventType());
        assertEquals("利空", event.getSignalSide());
        assertTrue(event.getSignalScore() >= 80);
    }

    private MacroNewsRaw raw(String sourceName, String sourceType, String title, String content) {
        MacroNewsRaw raw = new MacroNewsRaw();
        raw.setSourceName(sourceName);
        raw.setSourceType(sourceType);
        raw.setTitle(title);
        raw.setContent(content);
        raw.setPubDate(LocalDateTime.of(2026, 3, 16, 9, 30));
        return raw;
    }
}
