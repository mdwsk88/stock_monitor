package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStockSignalServiceTest {

    private AStockSignalService signalService;

    @BeforeEach
    void setUp() {
        signalService = new AStockSignalService(new StockFilterConfig());
    }

    @Test
    void enrichNotice_FiltersRoutinePledgeFreezeNotice() {
        AStockRss notice = buildNotice(
                "*ST亚太:关于实际控制人持有的控股股东股权质押及冻结的公告",
                "股份质押、冻结"
        );

        assertFalse(signalService.enrichNotice(notice));
        assertEquals("行政公告", notice.getEventType());
        assertEquals("噪音", notice.getSignalSide());
        assertEquals(0, notice.getSignalScore());
    }

    @Test
    void enrichNotice_FiltersRoutineBuybackProgressNotice() {
        AStockRss notice = buildNotice(
                "三旺通信:关于以集中竞价交易方式回购公司股份比例达到总股本1%暨回购进展公告",
                "回购"
        );

        assertFalse(signalService.enrichNotice(notice));
    }

    @Test
    void enrichNotice_FiltersCorrectionNotice() {
        AStockRss notice = buildNotice(
                "南威软件:关于控股股东部分股份质押的更正公告",
                "其他"
        );

        assertFalse(signalService.enrichNotice(notice));
    }

    @Test
    void enrichNotice_KeepsJudicialFreezeWithLitigationContext() {
        AStockRss notice = buildNotice(
                "某公司:关于控股股东股份被司法冻结及收到法院执行裁定的公告",
                "其他"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("诉讼仲裁", notice.getEventType());
        assertEquals("利空", notice.getSignalSide());
        assertTrue(notice.getSignalScore() >= 60);
    }

    @Test
    void enrichNotice_DowngradesPreliminaryRestructuringNotice() {
        AStockRss notice = buildNotice(
                "东吴证券:发行股份及支付现金购买资产暨关联交易预案",
                "其他"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("并购重组", notice.getEventType());
        assertEquals("中性", notice.getSignalSide());
        assertTrue(notice.getSignalScore() < 60);
    }

    @Test
    void enrichNotice_TreatsCreditorsMeetingAsRestructuringRisk() {
        AStockRss notice = buildNotice(
                "贝仕达克:关于控股子公司第一次债权人会议召开情况的公告",
                "其他"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("重整风险", notice.getEventType());
        assertEquals("利空", notice.getSignalSide());
        assertTrue(notice.getSignalScore() >= 60);
    }

    @Test
    void enrichNotice_FiltersBuybackCancellationAdjustmentNotice() {
        AStockRss notice = buildNotice(
                "爱玛科技:爱玛科技关于部分限制性股票回购注销完成调整“爱玛转债”转股价格的公告",
                "其他"
        );

        assertFalse(signalService.enrichNotice(notice));
        assertEquals("行政公告", notice.getEventType());
    }

    @Test
    void enrichNotice_RaisesMaterialTradingRiskNotice() {
        AStockRss notice = buildNotice(
                "宇环数控:关于公司股票交易风险提示性公告",
                "风险提示性公告"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("交易风险", notice.getEventType());
        assertEquals("利空", notice.getSignalSide());
        assertTrue(notice.getSignalScore() >= 68);
    }

    @Test
    void enrichNotice_RaisesDelistingRiskNotice() {
        AStockRss notice = buildNotice(
                "*ST精伦:关于公司股票可能被终止上市的风险提示公告",
                "风险提示性公告"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("退市风险", notice.getEventType());
        assertEquals("利空", notice.getSignalSide());
        assertTrue(notice.getSignalScore() >= 84);
    }

    @Test
    void enrichNotice_RaisesJudicialAuctionNotice() {
        AStockRss notice = buildNotice(
                "*ST亚太:关于控股股东所持部分股份将被司法拍卖的提示性公告",
                "其他"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertEquals("司法处置", notice.getEventType());
        assertEquals("利空", notice.getSignalSide());
        assertTrue(notice.getSignalScore() >= 72);
    }

    @Test
    void enrichNotice_DoesNotTreatDelistingReliefAsHighRisk() {
        AStockRss notice = buildNotice(
                "ST晨鸣:关于申请撤销其他风险警示的公告",
                "其他"
        );

        assertTrue(signalService.enrichNotice(notice));
        assertTrue(!"退市风险".equals(notice.getEventType()) || notice.getSignalScore() < 60);
    }

    @Test
    void buildClusterKey_GroupsPreliminaryRestructuringVariants() {
        AStockRss first = buildNotice(
                "东吴证券:关于筹划发行股份及支付现金购买资产事项的停牌公告",
                "其他"
        );
        first.setPubDate(LocalDateTime.of(2026, 3, 15, 10, 3));

        AStockRss second = buildNotice(
                "东吴证券:发行股份及支付现金购买资产暨关联交易预案",
                "其他"
        );
        second.setPubDate(LocalDateTime.of(2026, 3, 15, 10, 8));

        assertTrue(signalService.enrichNotice(first));
        assertTrue(signalService.enrichNotice(second));
        assertEquals(first.getClusterKey(), second.getClusterKey());
    }

    @Test
    void enrichNotice_FiltersRoutineRestructuringWrapperNotice() {
        AStockRss notice = buildNotice(
                "东吴证券:关于发行股份及支付现金购买资产暨关联交易相关方承诺事项的公告",
                "其他"
        );

        assertFalse(signalService.enrichNotice(notice));
        assertEquals("行政公告", notice.getEventType());
    }

    private AStockRss buildNotice(String title, String tag) {
        AStockRss notice = new AStockRss();
        notice.setStockCode("000001");
        notice.setStockName("测试公司");
        notice.setTitle(title);
        notice.setTag(tag);
        return notice;
    }
}
