package com.dawei.service.impl;

import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.entity.StockAlertDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 规则化生成机会榜中的龙头/跟风身位标签。
 */
@Component
public class AReportOpportunityInsightService {

    private static final List<String> HARD_CATALYST_KEYWORDS = List.of(
            "业绩", "预增", "扭亏", "中标", "订单", "合同", "回购", "增持",
            "并购", "重组", "获批", "投产", "涨价", "资产注入", "摘帽"
    );
    private static final List<String> SOFT_CATALYST_KEYWORDS = List.of(
            "战略合作", "签署协议", "设立", "框架", "意向", "参会", "投资者关系",
            "说明会", "回复函", "补充", "延期", "进展公告"
    );

    public List<AReportOpportunityInsight> buildInsights(List<StockAlertDTO<AStockRss>> opportunities,
                                                         List<AReportResonanceCard> resonanceCards,
                                                         MarketSnapshot marketSnapshot) {
        if (opportunities == null || opportunities.isEmpty()) {
            return List.of();
        }

        Map<String, AReportResonanceCard> resonanceByCode = resonanceCards == null
                ? Map.of()
                : resonanceCards.stream()
                .filter(Objects::nonNull)
                .filter(card -> StringUtils.isNotBlank(card.getStockCode()))
                .collect(Collectors.toMap(
                        AReportResonanceCard::getStockCode,
                        card -> card,
                        (left, right) -> left.getFusionScore() >= right.getFusionScore() ? left : right,
                        LinkedHashMap::new
                ));

        List<AReportOpportunityInsight> insights = new ArrayList<>();
        MarketState marketState = marketSnapshot != null && marketSnapshot.getMarketState() != null
                ? marketSnapshot.getMarketState()
                : MarketState.NEUTRAL;

        for (StockAlertDTO<AStockRss> alert : opportunities) {
            if (alert == null || alert.getStock() == null) {
                continue;
            }
            AStockRss stock = alert.getStock();
            AReportResonanceCard resonanceCard = resonanceByCode.get(stock.getStockCode());
            InsightScore insightScore = evaluate(alert, resonanceCard, marketState);
            String positionLabel = resolveLabel(alert, resonanceCard, marketState, insightScore.score());
            insights.add(new AReportOpportunityInsight(
                    stock.getStockCode(),
                    stock.getStockName(),
                    positionLabel,
                    buildReason(insightScore.reasons()),
                    buildTradeHint(positionLabel, marketState, resonanceCard != null),
                    insightScore.score(),
                    resonanceCard != null
            ));
        }

        return insights;
    }

    public AReportOpportunityInsight buildRealtimeInsight(AStockRss notice,
                                                          AStockRealtimeContext realtimeContext,
                                                          MarketSnapshot marketSnapshot) {
        if (notice == null || !"利多".equals(notice.getSignalSide())) {
            return null;
        }

        StockAlertDTO<AStockRss> alert = new StockAlertDTO<>(
                notice,
                safePositive(notice.getRawNoticeCount(), 1),
                safePositive(notice.getSignalScore(), 0),
                safePositive(notice.getEventCount(), 1),
                StringUtils.defaultIfBlank(notice.getSignalSide(), "中性")
        );

        List<AReportResonanceCard> resonanceCards = realtimeContext != null && realtimeContext.hasResonance()
                ? List.of(buildRealtimeResonanceCard(notice, realtimeContext, alert))
                : List.of();

        return buildInsights(List.of(alert), resonanceCards, marketSnapshot).stream()
                .findFirst()
                .orElse(null);
    }

    private InsightScore evaluate(StockAlertDTO<AStockRss> alert,
                                  AReportResonanceCard resonanceCard,
                                  MarketState marketState) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        AStockRss stock = alert.getStock();

        if (alert.getSignalScore() >= 110) {
            score += 38;
            reasons.add("事件评分进入主线级");
        } else if (alert.getSignalScore() >= 90) {
            score += 28;
            reasons.add("事件评分处于高优先级");
        } else if (alert.getSignalScore() >= 75) {
            score += 18;
            reasons.add("具备边际进攻弹性");
        } else {
            score += 8;
        }

        if (alert.getFrequency() >= 4) {
            score += 14;
            reasons.add("支撑公告密集");
        } else if (alert.getFrequency() >= 2) {
            score += 8;
            reasons.add("公告支撑数量尚可");
        }

        if (alert.getEventCount() >= 2) {
            score += 12;
            reasons.add("存在多事件簇抬升辨识度");
        }

        if (resonanceCard != null) {
            score += resonanceCard.getFusionScore() >= 130 ? 24 : 18;
            reasons.add("命中宏观主线共振");
        }

        String joinedText = normalize(String.join(" ",
                StringUtils.defaultString(stock.getEventType()),
                StringUtils.defaultString(stock.getTitle()),
                StringUtils.defaultString(stock.getAnalysisHint()),
                StringUtils.defaultString(stock.getClusterHighlights())
        ));
        if (containsAny(joinedText, HARD_CATALYST_KEYWORDS)) {
            score += 14;
            reasons.add("催化类型具备兑现性");
        }
        if (containsAny(joinedText, SOFT_CATALYST_KEYWORDS)) {
            score -= 10;
            reasons.add("催化更偏题材映射而非硬兑现");
        }

        switch (marketState) {
            case DEFENSIVE -> {
                if (resonanceCard == null) {
                    score -= 16;
                    reasons.add("防守态下独立催化承接偏弱");
                }
            }
            case RISK_ON -> {
                score += 6;
                reasons.add("进攻态提高资金容错率");
            }
            case OVERHEAT -> {
                if (resonanceCard != null) {
                    score += 4;
                    reasons.add("高潮态下核心共振仍具辨识度");
                } else {
                    score -= 8;
                    reasons.add("高潮态下后排追高容错下降");
                }
            }
            default -> {
            }
        }

        return new InsightScore(score, reasons);
    }

    private String resolveLabel(StockAlertDTO<AStockRss> alert,
                                AReportResonanceCard resonanceCard,
                                MarketState marketState,
                                int score) {
        boolean topScore = alert.getSignalScore() >= 110;
        boolean denseSupport = alert.getFrequency() >= 4;
        boolean strongResonance = resonanceCard != null && resonanceCard.getFusionScore() >= 100;

        if (marketState == MarketState.DEFENSIVE && !strongResonance && !topScore) {
            return "观察名单";
        }
        if (score >= 72 && (strongResonance || topScore || denseSupport)) {
            return "领军核心";
        }
        if (score >= 52) {
            return "高弹性跟风";
        }
        return "观察名单";
    }

    private String buildReason(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "当前仅保留为边际观察，不宜直接升级为主线锚点";
        }
        return reasons.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .limit(3)
                .collect(Collectors.joining("；"));
    }

    private String buildTradeHint(String positionLabel,
                                  MarketState marketState,
                                  boolean resonanceSupported) {
        return switch (positionLabel) {
            case "领军核心" -> switch (marketState) {
                case OVERHEAT -> "只跟踪分歧承接与换手强度，不建议在高潮尾段无脑追价";
                case DEFENSIVE -> "仅在逆势走强或获得主线确认时观察，不作为常规追涨标的";
                default -> resonanceSupported
                        ? "可作为主线锚点，优先观察开盘承接、量能和主题扩散"
                        : "可作为强催化主攻方向，但仍需确认板块资金是否跟随";
            };
            case "高弹性跟风" -> switch (marketState) {
                case OVERHEAT -> "更依赖板块延续，后排追高风险更高，宜等分歧确认";
                case DEFENSIVE -> "缺少逆势属性，更多作为题材温度计，不宜重仓博弈";
                default -> "更适合作为主线扩散补涨标的，需结合龙头强度同步观察";
            };
            default -> switch (marketState) {
                case RISK_ON -> "公告有边际催化，但尚未形成主线锚定，先放入观察名单";
                case OVERHEAT -> "情绪过热阶段更易掉队，若无新增强化，尽量避免尾盘追涨";
                default -> "先观察是否获得量能、主线和公告兑现的二次确认";
            };
        };
    }

    private AReportResonanceCard buildRealtimeResonanceCard(AStockRss notice,
                                                            AStockRealtimeContext realtimeContext,
                                                            StockAlertDTO<AStockRss> alert) {
        AReportResonanceCard card = new AReportResonanceCard();
        card.setStockCode(notice.getStockCode());
        card.setStockName(notice.getStockName());
        card.setSignalSide(StringUtils.defaultIfBlank(notice.getSignalSide(), "中性"));
        card.setFusionScore(safePositive(realtimeContext.getResonanceScore(), 0));
        card.setNoticeSignalScore(alert.getSignalScore());
        card.setMacroSignalScore(safePositive(realtimeContext.getMacroSignalScore(), 0));
        card.setEventClusterCount(alert.getEventCount());
        card.setSupportNoticeCount(alert.getFrequency());
        card.setNoticeEventType(notice.getEventType());
        card.setNoticeTitle(notice.getTitle());
        card.setNoticeAnalysisHint(notice.getAnalysisHint());
        card.setMacroThemeName(realtimeContext.getThemeName());
        card.setMacroTitle(realtimeContext.getMacroTitle());
        card.setMacroSummary(realtimeContext.getMacroSummary());
        card.setRelationReason(realtimeContext.getRelationReason());
        return card;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StringUtils.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .map(this::normalize)
                .anyMatch(text::contains);
    }

    private String normalize(String text) {
        return StringUtils.defaultString(text).toUpperCase(Locale.ROOT);
    }

    private int safePositive(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(value, fallback);
    }

    private record InsightScore(int score, List<String> reasons) {
    }
}
