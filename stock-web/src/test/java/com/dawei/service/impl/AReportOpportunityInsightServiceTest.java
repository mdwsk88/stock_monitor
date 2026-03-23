package com.dawei.service.impl;

import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.entity.StockAlertDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AReportOpportunityInsightServiceTest {

    private final AReportOpportunityInsightService insightService = new AReportOpportunityInsightService();

    @Test
    @DisplayName("主线级高分共振公告会被识别为领军核心")
    void buildInsights_ClassifiesLeader() {
        AReportOpportunityInsight insight = insightService.buildInsights(
                List.of(opportunityAlert(118, 4, 2)),
                List.of(resonanceCard(136)),
                snapshot(MarketState.RISK_ON)
        ).get(0);

        assertEquals("领军核心", insight.getPositionLabel());
        assertTrue(insight.getPositionReason().contains("主线级"));
        assertTrue(insight.isResonanceSupported());
    }

    @Test
    @DisplayName("高潮态下无共振的边际催化会降级为观察名单")
    void buildInsights_DowngradesWeakFollowerInOverheat() {
        AReportOpportunityInsight insight = insightService.buildInsights(
                List.of(opportunityAlert(72, 2, 1)),
                List.of(),
                snapshot(MarketState.OVERHEAT)
        ).get(0);

        assertEquals("观察名单", insight.getPositionLabel());
        assertTrue(insight.getTradeHint().contains("避免尾盘追涨"));
    }

    @Test
    @DisplayName("实时上下文可复用同一套身位判定")
    void buildRealtimeInsight_ReusesLeaderRules() {
        AStockRss notice = opportunityAlert(118, 4, 2).getStock();
        AStockRealtimeContext context = new AStockRealtimeContext(
                "算力",
                "算力基建提速",
                "政策继续加码",
                90,
                146,
                "公告直接命中主线",
                "EXPLICIT"
        );

        AReportOpportunityInsight insight = insightService.buildRealtimeInsight(
                notice,
                context,
                snapshot(MarketState.RISK_ON)
        );

        assertEquals("领军核心", insight.getPositionLabel());
        assertTrue(insight.getTradeHint().contains("主线锚点"));
    }

    private StockAlertDTO<AStockRss> opportunityAlert(int signalScore, int frequency, int eventCount) {
        AStockRss stock = new AStockRss();
        stock.setStockCode("000001");
        stock.setStockName("平安银行");
        stock.setTitle("平安银行:关于中标10亿元算力项目的公告");
        stock.setEventType(signalScore >= 100 ? "重大合同" : "战略合作");
        stock.setSignalSide("利多");
        stock.setSignalScore(signalScore);
        stock.setAnalysisHint("最高优先级事件为【" + stock.getEventType() + "】，方向=利多，总评分=" + signalScore + " 分");
        stock.setClusterHighlights(signalScore >= 100 ? "重大合同" : "战略合作");
        return new StockAlertDTO<>(stock, frequency, signalScore, eventCount, "利多");
    }

    private AReportResonanceCard resonanceCard(int fusionScore) {
        AReportResonanceCard card = new AReportResonanceCard();
        card.setStockCode("000001");
        card.setStockName("平安银行");
        card.setFusionScore(fusionScore);
        card.setMacroThemeName("算力");
        return card;
    }

    private MarketSnapshot snapshot(MarketState marketState) {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.of(2026, 3, 18, 9, 45), "TEST");
        snapshot.setMarketState(marketState);
        return snapshot;
    }
}
