package com.dawei.service.impl;

import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.entity.MarketState;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import org.springframework.stereotype.Service;

/**
 * A股盘中演示推送服务。
 * 使用 mock 数据走真实企业微信发送链路，方便对外展示盘中推送样式。
 */
@Service
public class AStockIntradayDemoPushService {

    private final WeComApi weComApi;
    private final PushLanguageService pushLanguageService;

    public AStockIntradayDemoPushService(WeComApi weComApi,
                                         PushLanguageService pushLanguageService) {
        this.weComApi = weComApi;
        this.pushLanguageService = pushLanguageService;
    }

    public DemoPushResult pushOpportunityDemo() {
        AStockRealtimeAlertCard card = buildOpportunityCard();
        String markdown = weComApi.formatAStockRealtimeAlert(card);
        weComApi.sendMarkdownMessage(markdown, WeComApi.MarketType.A);
        return new DemoPushResult(
                "intradayOpportunityDemo",
                pushLanguageService.text("盘中机会快讯（演示）", "Demo Intraday Opportunity Flash"),
                pushLanguageService.text("mock 数据已通过真实企业微信链路发送", "Mock data was sent through the real WeCom delivery path"),
                markdown,
                card,
                true
        );
    }

    public DemoPushResult pushRiskDemo() {
        AStockRealtimeAlertCard card = buildRiskCard();
        String markdown = weComApi.formatAStockRealtimeAlert(card);
        weComApi.sendMarkdownMessage(markdown, WeComApi.MarketType.A);
        return new DemoPushResult(
                "intradayRiskDemo",
                pushLanguageService.text("盘中风险快讯（演示）", "Demo Intraday Risk Flash"),
                pushLanguageService.text("mock 数据已通过真实企业微信链路发送", "Mock data was sent through the real WeCom delivery path"),
                markdown,
                card,
                true
        );
    }

    private AStockRealtimeAlertCard buildOpportunityCard() {
        AStockRealtimeAlertCard card = new AStockRealtimeAlertCard();
        card.setStockCode("301518");
        card.setStockName(pushLanguageService.text("灵境智控", "Lingjing Motion"));
        card.setPushType(AStockPushType.REALTIME_OPPORTUNITY);
        card.setSeverityLabel(pushLanguageService.text("核弹级催化", "Critical Catalyst"));
        card.setSignalScore(118);
        card.setEventType("重大合同");
        card.setTitle(pushLanguageService.text(
                "【演示数据】灵境智控：签下 7.2 亿元协作机器人核心部件量产订单",
                "[Demo Data] Lingjing Motion secured a CNY 720m mass-production order for collaborative robot components"
        ));
        card.setConclusion(pushLanguageService.text(
                "灵境智控刚披露主线级订单催化，适合作为盘中机会快讯的展示样例。",
                "Lingjing Motion just disclosed a main-theme-grade order catalyst and is suitable as a showcase intraday opportunity alert."
        ));
        card.setReasoning(pushLanguageService.text(
                "量产订单会直接抬升未来出货可见度，叠加机器人主线扩散，容易形成资金追踪焦点。",
                "A scaled production order directly lifts shipment visibility and can attract attention when the robotics theme is expanding."
        ));
        card.setRiskHint(pushLanguageService.text(
                "本条为演示数据，用于展示推送样式；真实盘中仍需确认量能、承接和板块扩散强度。",
                "This is demo data for showcasing the push layout. In live trading, confirmation from turnover, support, and theme expansion is still required."
        ));
        card.setMarketStateLabel(pushLanguageService.marketStateLabel(MarketState.RISK_ON));
        card.setPositionLabel("领军核心");
        card.setPositionReason("事件评分进入主线级");
        card.setTradeHint("可作为主线锚点，优先观察开盘承接、量能和主题扩散");
        card.setMacroThemeName(pushLanguageService.text("机器人 + 智能制造", "Robotics + Smart Manufacturing"));
        card.setMacroTitle(pushLanguageService.text(
                "工控与人形机器人催化继续强化，板块龙头带动扩散",
                "Robotics and industrial automation catalysts are strengthening again, with leaders driving broader participation"
        ));
        card.setMacroSignalScore(108);
        card.setResonanceScore(152);
        card.setRelationReason("公告直接命中主线");
        return card;
    }

    private AStockRealtimeAlertCard buildRiskCard() {
        AStockRealtimeAlertCard card = new AStockRealtimeAlertCard();
        card.setStockCode("603419");
        card.setStockName(pushLanguageService.text("博川新材", "Bochuan Materials"));
        card.setPushType(AStockPushType.REALTIME_RISK);
        card.setSeverityLabel(pushLanguageService.text("连续负面催化", "Stacked Negative Catalysts"));
        card.setSignalScore(96);
        card.setEventType("监管处罚");
        card.setTitle(pushLanguageService.text(
                "【演示数据】博川新材：收到立案告知并披露实控人减持计划",
                "[Demo Data] Bochuan Materials disclosed a regulatory filing notice alongside a controller sell-down plan"
        ));
        card.setConclusion(pushLanguageService.text(
                "博川新材的负面事件叠加明显，适合作为盘中风险预警展示样例。",
                "Bochuan Materials shows stacked negative catalysts and works well as a showcase intraday risk alert."
        ));
        card.setReasoning(pushLanguageService.text(
                "监管处罚叠加减持计划，通常会放大市场避险情绪，在防守态下更容易触发主动回避。",
                "Regulatory pressure combined with a sell-down plan usually amplifies risk aversion and is more likely to trigger active avoidance in a defensive tape."
        ));
        card.setRiskHint(pushLanguageService.text(
                "本条为演示数据，用于展示风险卡片样式；真实场景仍需结合成交、澄清公告和承接力度判断。",
                "This is demo data for showcasing the risk-card layout. In live conditions, volume, follow-up disclosures, and support still need to be checked."
        ));
        card.setMarketStateLabel(pushLanguageService.marketStateLabel(MarketState.DEFENSIVE));
        card.setMacroThemeName(pushLanguageService.text("监管趋严", "Tighter Regulation"));
        card.setMacroTitle(pushLanguageService.text(
                "监管口径偏紧，市场对高波动题材的容错率明显下降",
                "The regulatory tone is tightening and market tolerance for high-volatility themes is fading"
        ));
        card.setMacroSignalScore(94);
        card.setResonanceScore(126);
        card.setRelationReason("宏观主题与公告事件类型共振");
        return card;
    }

    public record DemoPushResult(String scenario,
                                 String title,
                                 String summary,
                                 String markdown,
                                 AStockRealtimeAlertCard card,
                                 boolean demoData) {
    }
}
