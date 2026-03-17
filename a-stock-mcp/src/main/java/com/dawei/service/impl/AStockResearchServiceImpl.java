package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.dto.AStockEventCard;
import com.dawei.dto.AStockSignalSummary;
import com.dawei.dto.StockResolveResult;
import com.dawei.entity.AStockRss;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.service.AStockResearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AStockResearchServiceImpl implements AStockResearchService {

    private static final int DEFAULT_RESOLVE_LIMIT = 5;
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_EVENT_LIMIT = 6;
    private static final int DEFAULT_SUMMARY_TOP_EVENTS = 4;
    private static final int DEFAULT_FETCH_LIMIT = 120;
    private static final int DEFAULT_SUMMARY_MIN_SCORE = 60;
    private static final int DEFAULT_OPPORTUNITY_MIN_SCORE = 80;
    private static final int DEFAULT_RISK_MIN_SCORE = 70;
    private static final int DEFAULT_RAW_MIN_SCORE = 50;
    private static final int DEFAULT_BOARD_HOURS = 24;

    @Resource
    private AStockRssMapper aStockRssMapper;

    @Override
    public List<StockResolveResult> resolveStocks(String stockQuery, Integer limit) {
        if (!StringUtils.hasText(stockQuery)) {
            return List.of();
        }

        String normalizedQuery = stockQuery.trim();
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("stock_code", "stock_name", "signal_score", "pub_date");
        queryWrapper.ge("signal_score", DEFAULT_RAW_MIN_SCORE);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS));
        queryWrapper.and(wrapper -> {
            if (isLikelyStockCode(normalizedQuery)) {
                wrapper.eq("stock_code", normalizedQuery)
                        .or()
                        .likeRight("stock_code", normalizedQuery)
                        .or()
                        .like("stock_name", normalizedQuery);
            } else {
                wrapper.like("stock_name", normalizedQuery);
            }
        });
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));

        List<AStockRss> notices = aStockRssMapper.selectList(queryWrapper);
        if (notices.isEmpty()) {
            return List.of();
        }

        List<StockResolveResult> resolved = aggregateResolveResults(normalizedQuery, notices);
        return resolved.subList(0, Math.min(normalizedLimit(limit, DEFAULT_RESOLVE_LIMIT, 10), resolved.size()));
    }

    @Override
    public AStockSignalSummary getStockSignalSummary(String stockQuery, Integer days) {
        StockResolveResult resolvedStock = resolveFirst(stockQuery);
        if (resolvedStock == null) {
            return emptySummary(stockQuery, normalizedWindowDays(days));
        }

        int lookbackDays = normalizedWindowDays(days);
        List<AStockRss> notices = fetchStockNotices(
                resolvedStock.getStockCode(),
                lookbackDays,
                DEFAULT_SUMMARY_MIN_SCORE,
                DEFAULT_FETCH_LIMIT
        );
        List<AStockEventCard> eventCards = buildEventCards(notices, DEFAULT_SUMMARY_TOP_EVENTS, false);
        int eventClusterCount = countEventClusters(notices);

        int bullishEventCount = countBySide(eventCards, "BUY");
        int bearishEventCount = countBySide(eventCards, "SELL");
        int neutralEventCount = countBySide(eventCards, "NEUTRAL");
        String dominantSignalSide = dominantSignalSide(eventCards);
        Integer topSignalScore = notices.stream()
                .map(AStockRss::getSignalScore)
                .max(Integer::compareTo)
                .orElse(0);
        LocalDateTime latestPubDate = notices.stream()
                .map(AStockRss::getPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        String analysisHint = buildSummaryHint(
                resolvedStock.getStockName(),
                lookbackDays,
                notices.size(),
                eventClusterCount,
                eventCards,
                dominantSignalSide
        );

        return new AStockSignalSummary(
                resolvedStock.getStockCode(),
                resolvedStock.getStockName(),
                stockQuery,
                lookbackDays,
                dominantSignalSide,
                bullishEventCount,
                bearishEventCount,
                neutralEventCount,
                notices.size(),
                eventClusterCount,
                topSignalScore,
                latestPubDate,
                analysisHint,
                eventCards
        );
    }

    @Override
    public List<AStockEventCard> getRecentEventCards(String stockQuery, Integer days, Integer minSignalScore, Integer limit) {
        StockResolveResult resolvedStock = resolveFirst(stockQuery);
        if (resolvedStock == null) {
            return List.of();
        }

        List<AStockRss> notices = fetchStockNotices(
                resolvedStock.getStockCode(),
                normalizedWindowDays(days),
                normalizedMinScore(minSignalScore, DEFAULT_SUMMARY_MIN_SCORE),
                fetchSizeForCards(limit)
        );
        return buildEventCards(notices, normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10), false);
    }

    @Override
    public List<AStockEventCard> getOpportunityBoard(Integer hours, Integer minSignalScore, Integer limit) {
        List<AStockRss> notices = fetchBoardNotices(
                normalizedWindowHours(hours),
                normalizedMinScore(minSignalScore, DEFAULT_OPPORTUNITY_MIN_SCORE),
                "BUY"
        );
        return buildBoardCards(notices, normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10));
    }

    @Override
    public List<AStockEventCard> getRiskBoard(Integer hours, Integer minSignalScore, Integer limit) {
        List<AStockRss> notices = fetchBoardNotices(
                normalizedWindowHours(hours),
                normalizedMinScore(minSignalScore, DEFAULT_RISK_MIN_SCORE),
                "SELL"
        );
        return buildBoardCards(notices, normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10));
    }

    @Override
    public List<AStockRss> queryRawAStockNotices(String stockQuery, Integer days, Integer limit) {
        StockResolveResult resolvedStock = resolveFirst(stockQuery);
        if (resolvedStock == null) {
            return List.of();
        }

        int normalizedLimit = normalizedLimit(limit, 5, 10);
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", resolvedStock.getStockCode());
        queryWrapper.ge("signal_score", DEFAULT_RAW_MIN_SCORE);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusDays(normalizedWindowDays(days)));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(normalizedLimit));
        return aStockRssMapper.selectList(queryWrapper);
    }

    private List<StockResolveResult> aggregateResolveResults(String stockQuery, List<AStockRss> notices) {
        Map<String, List<AStockRss>> grouped = notices.stream()
                .collect(Collectors.groupingBy(
                        notice -> notice.getStockCode() + ":" + notice.getStockName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(group -> {
                    AStockRss sample = group.get(0);
                    Integer topSignalScore = group.stream()
                            .map(AStockRss::getSignalScore)
                            .max(Integer::compareTo)
                            .orElse(0);
                    LocalDateTime latestPubDate = group.stream()
                            .map(AStockRss::getPubDate)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    String matchType = matchType(stockQuery, sample);
                    Integer confidence = matchConfidence(stockQuery, sample, topSignalScore);
                    return new StockResolveResult(
                            sample.getStockCode(),
                            sample.getStockName(),
                            matchType,
                            confidence,
                            group.size(),
                            topSignalScore,
                            latestPubDate
                    );
                })
                .sorted(Comparator
                        .comparing(StockResolveResult::getConfidence, Comparator.reverseOrder())
                        .thenComparing(StockResolveResult::getTopSignalScore, Comparator.reverseOrder())
                        .thenComparing(StockResolveResult::getLatestPubDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private List<AStockRss> fetchStockNotices(String stockCode, int lookbackDays, int minSignalScore, int limit) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.ge("signal_score", minSignalScore);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusDays(lookbackDays));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(limit));
        return aStockRssMapper.selectList(queryWrapper);
    }

    private List<AStockRss> fetchBoardNotices(int hours, int minSignalScore, String signalSide) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("signal_score", minSignalScore);
        queryWrapper.eq("signal_side", signalSide);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusHours(hours));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));
        return aStockRssMapper.selectList(queryWrapper);
    }

    private List<AStockEventCard> buildBoardCards(List<AStockRss> notices, int limit) {
        return buildEventCards(notices, limit, true);
    }

    private List<AStockEventCard> buildEventCards(List<AStockRss> notices, int limit, boolean oneCardPerStock) {
        if (notices.isEmpty()) {
            return List.of();
        }

        Map<String, List<AStockRss>> grouped = notices.stream()
                .collect(Collectors.groupingBy(this::eventGroupKey, LinkedHashMap::new, Collectors.toList()));

        List<AStockEventCard> sortedCards = grouped.values().stream()
                .map(this::toEventCard)
                .sorted(Comparator
                        .comparing(AStockEventCard::getSignalScore, Comparator.reverseOrder())
                        .thenComparing(AStockEventCard::getSupportNoticeCount, Comparator.reverseOrder())
                        .thenComparing(AStockEventCard::getLatestPubDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (!oneCardPerStock) {
            return sortedCards.subList(0, Math.min(limit, sortedCards.size()));
        }

        List<AStockEventCard> result = new ArrayList<>();
        Map<String, Boolean> seenStockCodes = new LinkedHashMap<>();
        for (AStockEventCard card : sortedCards) {
            if (seenStockCodes.putIfAbsent(card.getStockCode(), Boolean.TRUE) == null) {
                result.add(card);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private AStockEventCard toEventCard(List<AStockRss> clusterNotices) {
        List<AStockRss> sortedNotices = clusterNotices.stream()
                .sorted(Comparator
                        .comparing(AStockRss::getSignalScore, Comparator.reverseOrder())
                        .thenComparing(AStockRss::getPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        AStockRss representative = sortedNotices.get(0);
        LocalDateTime latestPubDate = clusterNotices.stream()
                .map(AStockRss::getPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(representative.getPubDate());
        String relatedTitles = clusterNotices.stream()
                .map(AStockRss::getTitle)
                .filter(title -> !Objects.equals(title, representative.getTitle()))
                .distinct()
                .limit(3)
                .collect(Collectors.joining("；"));
        String analysisHint = buildCardHint(representative, clusterNotices.size(), relatedTitles);
        return new AStockEventCard(
                representative.getStockCode(),
                representative.getStockName(),
                representative.getTitle(),
                representative.getEventType(),
                representative.getSignalSide(),
                representative.getSignalScore(),
                representative.getClusterKey(),
                representative.getTag(),
                latestPubDate,
                clusterNotices.size(),
                relatedTitles,
                analysisHint
        );
    }

    private String buildCardHint(AStockRss representative, int supportNoticeCount, String relatedTitles) {
        StringBuilder builder = new StringBuilder();
        builder.append(sideLabel(representative.getSignalSide()))
                .append("事件，重要性 ")
                .append(representative.getSignalScore())
                .append(" 分");
        if (supportNoticeCount > 1) {
            builder.append("，同簇公告 ").append(supportNoticeCount).append(" 条");
        }
        if (StringUtils.hasText(relatedTitles)) {
            builder.append("，补充标题：").append(relatedTitles);
        }
        return builder.toString();
    }

    private String buildSummaryHint(String stockName,
                                    int lookbackDays,
                                    int noticeCount,
                                    int eventClusterCount,
                                    List<AStockEventCard> eventCards,
                                    String dominantSignalSide) {
        String highlights = eventCards.stream()
                .map(AStockEventCard::getRepresentativeTitle)
                .limit(3)
                .collect(Collectors.joining("；"));
        if (!StringUtils.hasText(highlights)) {
            highlights = "暂无高价值事件";
        }
        return stockName + " 最近 " + lookbackDays + " 天共有 "
                + noticeCount + " 条高价值公告，聚合为 " + eventClusterCount
                + " 个核心事件，当前以" + sideLabel(dominantSignalSide) + "为主。重点看："
                + highlights;
    }

    private String eventGroupKey(AStockRss notice) {
        if (StringUtils.hasText(notice.getClusterKey())) {
            return notice.getClusterKey();
        }
        return notice.getStockCode() + ":" + notice.getEventType() + ":" + notice.getSignalSide() + ":" + notice.getTitle();
    }

    private StockResolveResult resolveFirst(String stockQuery) {
        List<StockResolveResult> resolved = resolveStocks(stockQuery, 1);
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    private AStockSignalSummary emptySummary(String query, int lookbackDays) {
        return new AStockSignalSummary(
                null,
                null,
                query,
                lookbackDays,
                "NEUTRAL",
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                "未解析到匹配的A股标的。",
                List.of()
        );
    }

    private int countBySide(List<AStockEventCard> eventCards, String signalSide) {
        return (int) eventCards.stream()
                .filter(card -> signalSide.equals(card.getSignalSide()))
                .count();
    }

    private int countEventClusters(List<AStockRss> notices) {
        return (int) notices.stream()
                .map(this::eventGroupKey)
                .distinct()
                .count();
    }

    private String dominantSignalSide(List<AStockEventCard> eventCards) {
        int buyScore = summedScoreBySide(eventCards, "BUY");
        int sellScore = summedScoreBySide(eventCards, "SELL");
        int neutralScore = summedScoreBySide(eventCards, "NEUTRAL");
        if (buyScore > sellScore && buyScore >= neutralScore) {
            return "BUY";
        }
        if (sellScore > buyScore && sellScore >= neutralScore) {
            return "SELL";
        }
        return "NEUTRAL";
    }

    private int summedScoreBySide(List<AStockEventCard> eventCards, String signalSide) {
        return eventCards.stream()
                .filter(card -> signalSide.equals(card.getSignalSide()))
                .map(AStockEventCard::getSignalScore)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
    }

    private boolean isLikelyStockCode(String stockQuery) {
        return stockQuery.chars().allMatch(Character::isDigit) && stockQuery.length() >= 3;
    }

    private String matchType(String stockQuery, AStockRss notice) {
        if (stockQuery.equals(notice.getStockCode())) {
            return "CODE_EXACT";
        }
        if (stockQuery.equals(notice.getStockName())) {
            return "NAME_EXACT";
        }
        if (notice.getStockName() != null && notice.getStockName().startsWith(stockQuery)) {
            return "NAME_PREFIX";
        }
        if (notice.getStockCode() != null && notice.getStockCode().startsWith(stockQuery)) {
            return "CODE_PREFIX";
        }
        return "NAME_FUZZY";
    }

    private Integer matchConfidence(String stockQuery, AStockRss notice, Integer topSignalScore) {
        int base = switch (matchType(stockQuery, notice)) {
            case "CODE_EXACT" -> 100;
            case "NAME_EXACT" -> 96;
            case "NAME_PREFIX" -> 88;
            case "CODE_PREFIX" -> 82;
            default -> 75;
        };
        return Math.min(100, base + Math.min(8, Math.max(0, topSignalScore - 60) / 5));
    }

    private String sideLabel(String signalSide) {
        return switch (signalSide) {
            case "BUY" -> "利多";
            case "SELL" -> "利空";
            default -> "中性";
        };
    }

    private int normalizedWindowDays(Integer requestedDays) {
        return normalizedLimit(requestedDays, DEFAULT_LOOKBACK_DAYS, 90);
    }

    private int normalizedWindowHours(Integer requestedHours) {
        return normalizedLimit(requestedHours, DEFAULT_BOARD_HOURS, 72);
    }

    private int normalizedMinScore(Integer requestedScore, int defaultScore) {
        if (requestedScore == null) {
            return defaultScore;
        }
        return Math.max(0, Math.min(100, requestedScore));
    }

    private int normalizedLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, maxLimit);
    }

    private int fetchSizeForCards(Integer limit) {
        int normalizedLimit = normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10);
        return Math.min(DEFAULT_FETCH_LIMIT, Math.max(24, normalizedLimit * 6));
    }

    private String limitClause(int limit) {
        return "LIMIT " + limit;
    }
}
