package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.dto.AStockEventCard;
import com.dawei.dto.AStockSignalSummary;
import com.dawei.dto.StockResolveResult;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import com.dawei.service.AStockResearchService;
import com.dawei.support.SignalSideSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final int DEFAULT_MACRO_MIN_SCORE = 75;
    private static final String AGGREGATE_SCORE_LABEL = "股票聚合分（晚报同算法）";
    private static final String CLUSTER_SCORE_LABEL = "事件簇分";

    private static final List<String> RISK_EVENT_KEYWORDS = List.of(
            "诉讼仲裁", "监管处罚", "业绩承压", "交易风险", "减持套现", "退市风险", "司法处置"
    );
    private static final List<String> RISK_TITLE_KEYWORDS = List.of(
            "退市风险", "立案", "处罚", "诉讼", "仲裁", "司法冻结", "司法拍卖",
            "减持", "异常波动", "终止上市", "风险提示"
    );
    private static final List<String> M_AND_A_MACRO_KEYWORDS = List.of(
            "并购", "重组", "资产注入", "发行股份购买资产", "国企改革", "国资国企", "央企重组", "收购"
    );
    private static final List<String> M_AND_A_NOTICE_KEYWORDS = List.of(
            "并购重组", "重大资产重组", "发行股份购买资产", "收购出售资产", "资产注入", "购买资产", "收购"
    );
    private static final Map<String, List<String>> THEME_KEYWORDS = Map.ofEntries(
            Map.entry("国企改革", List.of("国企改革", "国资国企", "央企重组", "资产注入", "国企并购")),
            Map.entry("并购重组", List.of("并购", "重组", "资产注入", "发行股份购买资产", "收购")),
            Map.entry("金融", List.of("金融", "券商", "保险", "银行", "资本市场")),
            Map.entry("稳增长", List.of("稳增长", "基建", "专项债", "重大项目", "设备更新", "工程机械")),
            Map.entry("算力", List.of("算力", "智算", "数据中心", "服务器", "光模块")),
            Map.entry("人工智能", List.of("人工智能", "大模型", "aigc", "智能体")),
            Map.entry("低空经济", List.of("低空经济", "飞行汽车", "无人机", "通航", "evtol")),
            Map.entry("机器人", List.of("机器人", "人形机器人", "工业机器人", "机器狗")),
            Map.entry("半导体", List.of("半导体", "芯片", "晶圆", "封装", "存储")),
            Map.entry("创新药", List.of("创新药", "药物", "双抗", "adc", "car-t")),
            Map.entry("军工", List.of("军工", "国防", "导弹", "卫星")),
            Map.entry("稀土", List.of("稀土", "镨钕", "氧化镨钕")),
            Map.entry("光伏", List.of("光伏", "硅料", "硅片", "逆变器")),
            Map.entry("锂电", List.of("锂电", "电池", "锂矿", "固态电池")),
            Map.entry("黄金", List.of("黄金", "金价")),
            Map.entry("原油", List.of("原油", "油价", "布伦特", "wti")),
            Map.entry("天然气", List.of("天然气", "lng"))
    );

    @Resource
    private AStockRssMapper aStockRssMapper;

    @Resource
    private MacroThemeEventMapper macroThemeEventMapper;

    @Resource
    private MacroThemeStockRelMapper macroThemeStockRelMapper;

    @Resource
    private ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;

    @Override
    public List<StockResolveResult> resolveStocks(String stockQuery, Integer limit) {
        if (!StringUtils.hasText(stockQuery)) {
            return List.of();
        }

        String normalizedQuery = stockQuery.trim();
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("stock_code", "stock_name", "signal_score", "signal_side", "pub_date");
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

        List<AStockRss> notices = normalizeNotices(aStockRssMapper.selectList(queryWrapper));
        if (notices.isEmpty()) {
            return List.of();
        }

        List<StockResolveResult> resolved = aggregateResolveResults(normalizedQuery, notices);
        return resolved.subList(0, Math.min(normalizedLimit(limit, DEFAULT_RESOLVE_LIMIT, 10), resolved.size()));
    }

    @Override
    public AStockSignalSummary getStockSignalSummary(String stockQuery, Integer days) {
        StockResolveResult resolvedStock = resolveFirst(stockQuery);
        int lookbackDays = normalizedWindowDays(days);
        if (resolvedStock == null) {
            return emptySummary(stockQuery, lookbackDays);
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(lookbackDays);
        List<AStockRss> notices = fetchStockNotices(
                resolvedStock.getStockCode(),
                lookbackDays,
                DEFAULT_SUMMARY_MIN_SCORE,
                DEFAULT_FETCH_LIMIT
        );
        StockAggregateView aggregateView = buildStockAggregateView(
                resolvedStock.getStockCode(),
                resolvedStock.getStockName(),
                notices,
                endTime
        );
        if (aggregateView == null) {
            return emptySummary(stockQuery, lookbackDays);
        }

        String aggregateScoreWindow = summaryWindowLabel(lookbackDays);
        String scoreComparisonNote = summaryComparisonNote(lookbackDays);
        List<AStockEventCard> eventCards = aggregateView.clusters().stream()
                .limit(DEFAULT_SUMMARY_TOP_EVENTS)
                .map(cluster -> toClusterEventCard(
                        cluster,
                        aggregateView.aggregateSignalScore(),
                        clusterScoreWindowLabel(lookbackDays),
                        clusterComparisonNote()
                ))
                .toList();
        ResonanceView resonanceView = findBestResonance(aggregateView, startTime, endTime);

        int bullishEventCount = countClustersBySide(aggregateView.clusters(), SignalSideSupport.BUY);
        int bearishEventCount = countClustersBySide(aggregateView.clusters(), SignalSideSupport.SELL);
        int neutralEventCount = countClustersBySide(aggregateView.clusters(), SignalSideSupport.NEUTRAL);
        int topRawSignalScore = aggregateView.clusters().stream()
                .map(ClusterView::rawSignalScore)
                .max(Integer::compareTo)
                .orElse(0);

        return new AStockSignalSummary(
                resolvedStock.getStockCode(),
                resolvedStock.getStockName(),
                stockQuery,
                lookbackDays,
                aggregateView.dominantSignalSide(),
                bullishEventCount,
                bearishEventCount,
                neutralEventCount,
                aggregateView.highValueNoticeCount(),
                aggregateView.eventClusterCount(),
                aggregateView.aggregateSignalScore(),
                aggregateView.aggregateSignalScore(),
                topRawSignalScore,
                SignalSideSupport.toLabel(aggregateView.dominantSignalSide()),
                aggregateView.latestPubDate(),
                resonanceView.themeName(),
                resonanceView.fusionScore(),
                resonanceView.macroSignalScore(),
                resonanceView.macroTitle(),
                resonanceView.relationReason(),
                buildSummaryHint(aggregateView, resonanceView, aggregateScoreWindow, scoreComparisonNote),
                AGGREGATE_SCORE_LABEL,
                aggregateScoreWindow,
                scoreComparisonNote,
                eventCards
        );
    }

    @Override
    public List<AStockEventCard> getRecentEventCards(String stockQuery, Integer days, Integer minSignalScore, Integer limit) {
        StockResolveResult resolvedStock = resolveFirst(stockQuery);
        if (resolvedStock == null) {
            return List.of();
        }

        int lookbackDays = normalizedWindowDays(days);
        LocalDateTime endTime = LocalDateTime.now();
        List<AStockRss> notices = fetchStockNotices(
                resolvedStock.getStockCode(),
                lookbackDays,
                normalizedMinScore(minSignalScore, DEFAULT_SUMMARY_MIN_SCORE),
                fetchSizeForCards(limit)
        );
        StockAggregateView aggregateView = buildStockAggregateView(
                resolvedStock.getStockCode(),
                resolvedStock.getStockName(),
                notices,
                endTime
        );
        if (aggregateView == null) {
            return List.of();
        }
        String clusterScoreWindow = clusterScoreWindowLabel(lookbackDays);
        String clusterComparisonNote = clusterComparisonNote();
        return aggregateView.clusters().stream()
                .limit(normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10))
                .map(cluster -> toClusterEventCard(
                        cluster,
                        aggregateView.aggregateSignalScore(),
                        clusterScoreWindow,
                        clusterComparisonNote
                ))
                .toList();
    }

    @Override
    public List<AStockEventCard> getOpportunityBoard(Integer hours, Integer minSignalScore, Integer limit) {
        return getBoardCards(normalizedWindowHours(hours), normalizedMinScore(minSignalScore, DEFAULT_OPPORTUNITY_MIN_SCORE),
                normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10), false);
    }

    @Override
    public List<AStockEventCard> getRiskBoard(Integer hours, Integer minSignalScore, Integer limit) {
        return getBoardCards(normalizedWindowHours(hours), normalizedMinScore(minSignalScore, DEFAULT_RISK_MIN_SCORE),
                normalizedLimit(limit, DEFAULT_EVENT_LIMIT, 10), true);
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
        return normalizeNotices(aStockRssMapper.selectList(queryWrapper));
    }

    private List<AStockEventCard> getBoardCards(int hours, int minSignalScore, int limit, boolean riskBoard) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        List<AStockRss> notices = fetchBoardCandidateNotices(hours, minSignalScore);
        if (notices.isEmpty()) {
            return List.of();
        }

        List<StockAggregateView> views = notices.stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(group -> buildStockAggregateView(group.get(0).getStockCode(), group.get(0).getStockName(), group, endTime))
                .filter(Objects::nonNull)
                .filter(view -> riskBoard == isRiskAggregate(view))
                .sorted(Comparator
                        .comparing(StockAggregateView::aggregateSignalScore, Comparator.reverseOrder())
                        .thenComparing(StockAggregateView::eventClusterCount, Comparator.reverseOrder())
                        .thenComparing(StockAggregateView::latestPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<AStockEventCard> result = new ArrayList<>();
        String boardScoreWindow = boardWindowLabel(hours);
        String boardComparisonNote = boardComparisonNote(hours);
        for (StockAggregateView view : views) {
            ResonanceView resonanceView = findBestResonance(view, startTime, endTime);
            result.add(toBoardCard(view, resonanceView, boardScoreWindow, boardComparisonNote));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
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
                            .filter(Objects::nonNull)
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
        return normalizeNotices(aStockRssMapper.selectList(queryWrapper));
    }

    private List<AStockRss> fetchBoardCandidateNotices(int hours, int minSignalScore) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("signal_score", minSignalScore);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusHours(hours));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));
        return normalizeNotices(aStockRssMapper.selectList(queryWrapper));
    }

    private List<AStockRss> normalizeNotices(List<AStockRss> notices) {
        if (notices == null || notices.isEmpty()) {
            return List.of();
        }
        notices.forEach(this::normalizeNotice);
        return notices;
    }

    private void normalizeNotice(AStockRss notice) {
        if (notice == null) {
            return;
        }
        notice.setSignalSide(SignalSideSupport.normalize(notice.getSignalSide()));
    }

    private StockAggregateView buildStockAggregateView(String stockCode,
                                                       String stockName,
                                                       List<AStockRss> notices,
                                                       LocalDateTime endTime) {
        if (notices == null || notices.isEmpty()) {
            return null;
        }

        List<ClusterView> clusters = notices.stream()
                .collect(Collectors.groupingBy(this::eventGroupKey, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(this::toClusterView)
                .sorted(Comparator
                        .comparing(ClusterView::clusterScore, Comparator.reverseOrder())
                        .thenComparing(ClusterView::latestPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (clusters.isEmpty()) {
            return null;
        }

        int aggregateSignalScore = computeStockAggregateScore(clusters, endTime);
        LocalDateTime latestPubDate = clusters.stream()
                .map(ClusterView::latestPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        String dominantSignalSide = resolveOverallSignalSide(clusters);
        ClusterView topCluster = clusters.get(0);
        String analysisHint = stockName + " 最近共有 " + notices.size() + " 条高价值公告，聚合为 "
                + clusters.size() + " 个核心事件，当前以" + SignalSideSupport.toLabel(dominantSignalSide)
                + "为主。主导事件是【" + safeText(topCluster.eventType()) + "】，聚合评分 "
                + aggregateSignalScore + " 分。";
        String clusterHighlights = clusters.stream()
                .limit(3)
                .map(cluster -> String.format("%s | %s | 簇评分=%d | 支撑公告=%d | 代表标题=%s",
                        safeText(cluster.eventType()),
                        SignalSideSupport.toLabel(cluster.signalSide()),
                        cluster.clusterScore(),
                        cluster.noticeCount(),
                        safeText(cluster.representative().getTitle())))
                .collect(Collectors.joining("\n"));

        return new StockAggregateView(
                stockCode,
                stockName,
                clusters,
                aggregateSignalScore,
                notices.size(),
                clusters.size(),
                dominantSignalSide,
                latestPubDate,
                analysisHint,
                clusterHighlights,
                topCluster
        );
    }

    private ClusterView toClusterView(List<AStockRss> clusterNotices) {
        List<AStockRss> sortedNotices = clusterNotices.stream()
                .peek(this::normalizeNotice)
                .sorted(Comparator
                        .comparing((AStockRss notice) -> safeInt(notice.getSignalScore())).reversed()
                        .thenComparing(AStockRss::getPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        AStockRss representative = sortedNotices.get(0);
        int rawSignalScore = safeInt(representative.getSignalScore());
        int supportBonus = Math.min(18, Math.max(0, sortedNotices.size() - 1) * 6);
        int clusterScore = Math.min(120, rawSignalScore + supportBonus);
        LocalDateTime latestPubDate = sortedNotices.stream()
                .map(AStockRss::getPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(representative.getPubDate());
        String titles = sortedNotices.stream()
                .map(AStockRss::getTitle)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(" | "));

        return new ClusterView(
                representative,
                rawSignalScore,
                clusterScore,
                sortedNotices.size(),
                latestPubDate,
                representative.getSignalSide(),
                titles,
                representative.getEventType()
        );
    }

    private int computeStockAggregateScore(List<ClusterView> clusters, LocalDateTime endTime) {
        int total = 0;
        for (int i = 0; i < clusters.size(); i++) {
            ClusterView cluster = clusters.get(i);
            double weight = i == 0 ? 1.0 : (i == 1 ? 0.45 : 0.25);
            total += (int) Math.round(cluster.clusterScore() * weight);
        }
        total += Math.min(12, Math.max(0, clusters.size() - 1) * 4);
        ClusterView topCluster = clusters.get(0);
        if (topCluster.latestPubDate() != null && topCluster.latestPubDate().isAfter(endTime.minusHours(6))) {
            total += 6;
        }
        return Math.min(total, 180);
    }

    private String resolveOverallSignalSide(List<ClusterView> clusters) {
        long bullish = clusters.stream().filter(cluster -> SignalSideSupport.BUY.equals(cluster.signalSide())).count();
        long bearish = clusters.stream().filter(cluster -> SignalSideSupport.SELL.equals(cluster.signalSide())).count();
        if (bullish > bearish) {
            return SignalSideSupport.BUY;
        }
        if (bearish > bullish) {
            return SignalSideSupport.SELL;
        }
        return clusters.get(0).signalSide();
    }

    private int countClustersBySide(List<ClusterView> clusters, String signalSide) {
        return (int) clusters.stream()
                .filter(cluster -> signalSide.equals(cluster.signalSide()))
                .count();
    }

    private AStockEventCard toClusterEventCard(ClusterView cluster,
                                               int stockAggregateScore,
                                               String scoreWindow,
                                               String scoreComparisonNote) {
        AStockEventCard card = new AStockEventCard();
        card.setStockCode(cluster.representative().getStockCode());
        card.setStockName(cluster.representative().getStockName());
        card.setRepresentativeTitle(cluster.representative().getTitle());
        card.setEventType(cluster.eventType());
        card.setSignalSide(cluster.signalSide());
        card.setSignalScore(cluster.clusterScore());
        card.setRawSignalScore(cluster.rawSignalScore());
        card.setStockAggregateScore(stockAggregateScore);
        card.setScoreType("CLUSTER");
        card.setClusterKey(cluster.representative().getClusterKey());
        card.setTag(cluster.representative().getTag());
        card.setLatestPubDate(cluster.latestPubDate());
        card.setSupportNoticeCount(cluster.noticeCount());
        card.setEventClusterCount(1);
        card.setRelatedTitles(relatedTitlesExcludingRepresentative(cluster));
        card.setAnalysisHint(buildClusterHint(cluster, scoreWindow));
        card.setScoreLabel(CLUSTER_SCORE_LABEL);
        card.setScoreWindow(scoreWindow);
        card.setScoreComparisonNote(scoreComparisonNote);
        return card;
    }

    private AStockEventCard toBoardCard(StockAggregateView view,
                                        ResonanceView resonanceView,
                                        String scoreWindow,
                                        String scoreComparisonNote) {
        ClusterView topCluster = view.topCluster();
        AStockEventCard card = new AStockEventCard();
        card.setStockCode(view.stockCode());
        card.setStockName(view.stockName());
        card.setRepresentativeTitle(topCluster.representative().getTitle());
        card.setEventType(view.clusters().stream()
                .map(ClusterView::eventType)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(" | ")));
        card.setSignalSide(view.dominantSignalSide());
        card.setSignalScore(view.aggregateSignalScore());
        card.setRawSignalScore(topCluster.rawSignalScore());
        card.setStockAggregateScore(view.aggregateSignalScore());
        card.setFusionScore(resonanceView.fusionScore());
        card.setScoreType("STOCK_AGGREGATE");
        card.setClusterKey(topCluster.representative().getClusterKey());
        card.setTag(topCluster.representative().getTag());
        card.setLatestPubDate(view.latestPubDate());
        card.setSupportNoticeCount(view.highValueNoticeCount());
        card.setEventClusterCount(view.eventClusterCount());
        card.setMacroThemeName(resonanceView.themeName());
        card.setMacroSignalScore(resonanceView.macroSignalScore());
        card.setRelationReason(resonanceView.relationReason());
        card.setRelatedTitles(aggregateClusterTitles(view.clusters()));
        card.setAnalysisHint(buildBoardHint(view, resonanceView, scoreWindow, scoreComparisonNote));
        card.setScoreLabel(AGGREGATE_SCORE_LABEL);
        card.setScoreWindow(scoreWindow);
        card.setScoreComparisonNote(scoreComparisonNote);
        return card;
    }

    private ResonanceView findBestResonance(StockAggregateView aggregateView,
                                            LocalDateTime startTime,
                                            LocalDateTime endTime) {
        if (aggregateView == null) {
            return ResonanceView.empty();
        }

        QueryWrapper<MacroThemeEvent> eventWrapper = new QueryWrapper<>();
        eventWrapper.ge("signal_score", DEFAULT_MACRO_MIN_SCORE);
        eventWrapper.ge("pub_date", startTime);
        eventWrapper.le("pub_date", endTime);
        eventWrapper.orderByDesc("signal_score")
                .orderByDesc("importance_level")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));
        List<MacroThemeEvent> macroEvents = normalizeMacroEvents(macroThemeEventMapper.selectList(eventWrapper));
        if (macroEvents.isEmpty()) {
            return ResonanceView.empty();
        }

        List<String> eventIds = macroEvents.stream()
                .map(MacroThemeEvent::getId)
                .filter(StringUtils::hasText)
                .toList();
        Map<String, MacroThemeStockRel> relationByEventId = eventIds.isEmpty() ? Map.of()
                : macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .in("theme_event_id", eventIds)
                .eq("stock_code", aggregateView.stockCode()))
                .stream()
                .collect(Collectors.toMap(MacroThemeStockRel::getThemeEventId, relation -> relation,
                        (left, right) -> safeInt(left.getConfidence()) >= safeInt(right.getConfidence()) ? left : right));

        Map<String, ThemeAutoPoolCandidate> candidateByThemeName = themeAutoPoolCandidateMapper.selectList(new QueryWrapper<ThemeAutoPoolCandidate>()
                        .eq("stock_code", aggregateView.stockCode())
                        .eq("enabled", 1)
                        .ge("latest_pub_date", startTime)
                        .orderByDesc("candidate_score")
                        .last(limitClause(12)))
                .stream()
                .collect(Collectors.toMap(ThemeAutoPoolCandidate::getThemeName, candidate -> candidate,
                        (left, right) -> safeInt(left.getCandidateScore()) >= safeInt(right.getCandidateScore()) ? left : right));

        ResonanceView best = ResonanceView.empty();
        for (MacroThemeEvent event : macroEvents) {
            if (!SignalSideSupport.isCompatible(aggregateView.dominantSignalSide(), event.getSignalSide())) {
                continue;
            }

            String relationReason = null;
            MacroThemeStockRel relation = relationByEventId.get(event.getId());
            if (relation != null) {
                relationReason = StringUtils.hasText(relation.getReason()) ? relation.getReason() : "宏观主题映射命中";
            } else if (candidateByThemeName.containsKey(event.getThemeName())) {
                ThemeAutoPoolCandidate candidate = candidateByThemeName.get(event.getThemeName());
                relationReason = StringUtils.hasText(candidate.getReason()) ? candidate.getReason() : "主题自动候选池命中";
            } else if (matchesMacroEventResonance(aggregateView, event)) {
                relationReason = "宏观主题与公告事件类型共振";
            } else if (matchesThemeKeywordResonance(aggregateView, event)) {
                relationReason = "宏观主题关键词与公告主题一致";
            }

            if (!StringUtils.hasText(relationReason)) {
                continue;
            }

            int fusionScore = computeFusionScore(aggregateView, event, endTime);
            ResonanceView candidate = new ResonanceView(
                    event.getThemeName(),
                    fusionScore,
                    safeInt(event.getSignalScore()),
                    event.getTitle(),
                    relationReason
            );
            if (candidate.betterThan(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private List<MacroThemeEvent> normalizeMacroEvents(List<MacroThemeEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        events.forEach(event -> event.setSignalSide(SignalSideSupport.normalize(event.getSignalSide())));
        return events;
    }

    private int computeFusionScore(StockAggregateView aggregateView, MacroThemeEvent macroTheme, LocalDateTime endTime) {
        int noticeScore = Math.max(0, aggregateView.aggregateSignalScore());
        int macroScore = safeInt(macroTheme.getSignalScore());
        int total = Math.max(noticeScore, macroScore);
        total += (int) Math.round(Math.min(noticeScore, macroScore) * 0.35);
        total += Math.min(12, aggregateView.eventClusterCount() * 3);
        total += Math.min(8, Math.max(0, aggregateView.highValueNoticeCount() - 1) * 2);

        if (aggregateView.dominantSignalSide().equals(macroTheme.getSignalSide())) {
            total += 12;
        } else if (SignalSideSupport.NEUTRAL.equals(aggregateView.dominantSignalSide())
                || SignalSideSupport.NEUTRAL.equals(macroTheme.getSignalSide())) {
            total += 6;
        }

        if (macroTheme.getPubDate() != null && macroTheme.getPubDate().isAfter(endTime.minusHours(6))) {
            total += 6;
        }
        return Math.min(180, total);
    }

    private boolean matchesThemeKeywordResonance(StockAggregateView aggregateView, MacroThemeEvent macroTheme) {
        List<String> keywords = THEME_KEYWORDS.get(macroTheme.getThemeName());
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String macroText = normalizeText(joinText(
                macroTheme.getThemeName(),
                macroTheme.getEventType(),
                macroTheme.getTitle(),
                macroTheme.getSummary()
        ));
        String noticeText = normalizeText(joinText(
                aggregateView.topCluster().eventType(),
                aggregateView.topCluster().representative().getTitle(),
                aggregateView.analysisHint(),
                aggregateView.clusterHighlights()
        ));
        return containsAnyKeyword(macroText, keywords) && containsAnyKeyword(noticeText, keywords);
    }

    private boolean matchesMacroEventResonance(StockAggregateView aggregateView, MacroThemeEvent macroTheme) {
        String macroText = normalizeText(joinText(
                macroTheme.getThemeName(),
                macroTheme.getEventType(),
                macroTheme.getTitle(),
                macroTheme.getSummary()
        ));
        if (!containsAnyKeyword(macroText, M_AND_A_MACRO_KEYWORDS)) {
            return false;
        }
        String noticeText = normalizeText(joinText(
                aggregateView.topCluster().eventType(),
                aggregateView.topCluster().representative().getTitle(),
                aggregateView.analysisHint(),
                aggregateView.clusterHighlights()
        ));
        return containsAnyKeyword(noticeText, M_AND_A_NOTICE_KEYWORDS);
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeText)
                .anyMatch(text::contains);
    }

    private boolean isRiskAggregate(StockAggregateView view) {
        if (SignalSideSupport.SELL.equals(view.dominantSignalSide())) {
            return true;
        }
        if (containsAnyKeyword(normalizeText(view.topCluster().eventType()), RISK_EVENT_KEYWORDS)) {
            return true;
        }
        if (containsAnyKeyword(normalizeText(view.topCluster().representative().getTitle()), RISK_TITLE_KEYWORDS)
                || containsAnyKeyword(normalizeText(view.analysisHint()), RISK_TITLE_KEYWORDS)) {
            return true;
        }
        return StringUtils.hasText(view.stockName()) && view.stockName().toUpperCase(Locale.ROOT).contains("ST");
    }

    private String buildSummaryHint(StockAggregateView aggregateView,
                                    ResonanceView resonanceView,
                                    String aggregateScoreWindow,
                                    String scoreComparisonNote) {
        StringBuilder builder = new StringBuilder(aggregateView.analysisHint());
        builder.append(" 当前查询口径=").append(aggregateScoreWindow).append("。");
        builder.append(" 晚报同算法聚合分=").append(aggregateView.aggregateSignalScore())
                .append("，原始最高公告分=").append(aggregateView.topCluster().rawSignalScore()).append("。");
        if (resonanceView.hasResonance()) {
            builder.append(" 当前最佳主题共振为【").append(resonanceView.themeName())
                    .append("】，融合分 ").append(resonanceView.fusionScore()).append("。");
        }
        builder.append(' ').append(scoreComparisonNote);
        return builder.toString();
    }

    private String buildBoardHint(StockAggregateView aggregateView,
                                  ResonanceView resonanceView,
                                  String scoreWindow,
                                  String scoreComparisonNote) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前查询口径=").append(scoreWindow).append("。");
        builder.append(" 晚报同算法聚合分 ").append(aggregateView.aggregateSignalScore())
                .append(" 分，事件簇 ").append(aggregateView.eventClusterCount())
                .append(" 个，支撑公告 ").append(aggregateView.highValueNoticeCount()).append(" 条。");
        builder.append(" 主导方向为").append(SignalSideSupport.toLabel(aggregateView.dominantSignalSide())).append("。");
        if (resonanceView.hasResonance()) {
            builder.append(" 与【").append(resonanceView.themeName()).append("】形成共振，融合分 ")
                    .append(resonanceView.fusionScore()).append("。");
        }
        builder.append(' ').append(scoreComparisonNote);
        return builder.toString();
    }

    private String buildClusterHint(ClusterView cluster, String scoreWindow) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前查询口径=").append(scoreWindow).append("。 ");
        builder.append(SignalSideSupport.toLabel(cluster.signalSide()))
                .append("事件，簇评分 ").append(cluster.clusterScore())
                .append(" 分，原始最高公告分 ").append(cluster.rawSignalScore())
                .append(" 分");
        if (cluster.noticeCount() > 1) {
            builder.append("，同簇公告 ").append(cluster.noticeCount()).append(" 条");
        }
        String relatedTitles = relatedTitlesExcludingRepresentative(cluster);
        if (StringUtils.hasText(relatedTitles)) {
            builder.append("，补充标题：").append(relatedTitles);
        }
        return builder.toString();
    }

    private String relatedTitlesExcludingRepresentative(ClusterView cluster) {
        return cluster.titles().isBlank()
                ? ""
                : java.util.Arrays.stream(cluster.titles().split("\\s*\\|\\s*"))
                .filter(StringUtils::hasText)
                .filter(title -> !title.equals(cluster.representative().getTitle()))
                .distinct()
                .limit(3)
                .collect(Collectors.joining("；"));
    }

    private String aggregateClusterTitles(List<ClusterView> clusters) {
        return clusters.stream()
                .map(ClusterView::titles)
                .filter(StringUtils::hasText)
                .flatMap(titles -> java.util.Arrays.stream(titles.split("\\s*\\|\\s*")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10)
                .collect(Collectors.joining(" | "));
    }

    private String eventGroupKey(AStockRss notice) {
        if (StringUtils.hasText(notice.getClusterKey())) {
            return notice.getClusterKey();
        }
        return notice.getStockCode() + ":" + safeText(notice.getEventType()) + ":"
                + SignalSideSupport.normalize(notice.getSignalSide()) + ":" + safeText(notice.getTitle());
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
                SignalSideSupport.NEUTRAL,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                SignalSideSupport.toLabel(SignalSideSupport.NEUTRAL),
                null,
                null,
                0,
                0,
                null,
                null,
                "未解析到匹配的A股标的。",
                AGGREGATE_SCORE_LABEL,
                summaryWindowLabel(lookbackDays),
                summaryComparisonNote(lookbackDays),
                List.of()
        );
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
        if (StringUtils.hasText(notice.getStockName()) && notice.getStockName().startsWith(stockQuery)) {
            return "NAME_PREFIX";
        }
        if (StringUtils.hasText(notice.getStockCode()) && notice.getStockCode().startsWith(stockQuery)) {
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
        return Math.min(100, base + Math.min(8, Math.max(0, safeInt(topSignalScore) - 60) / 5));
    }

    private int normalizedWindowDays(Integer requestedDays) {
        return normalizedLimit(requestedDays, DEFAULT_LOOKBACK_DAYS, 90);
    }

    private int normalizedWindowHours(Integer requestedHours) {
        return normalizedLimit(requestedHours, DEFAULT_BOARD_HOURS, 72);
    }

    private String summaryWindowLabel(int lookbackDays) {
        if (lookbackDays <= 1) {
            return "最近24小时滚动窗口";
        }
        return "最近" + lookbackDays + "天滚动窗口";
    }

    private String boardWindowLabel(int hours) {
        return "最近" + hours + "小时滚动窗口";
    }

    private String clusterScoreWindowLabel(int lookbackDays) {
        return summaryWindowLabel(lookbackDays) + "（按 clusterKey 聚合）";
    }

    private String summaryComparisonNote(int lookbackDays) {
        if (lookbackDays <= 1) {
            return "当前为最近24小时滚动窗口，接近盘前/盘后榜单口径，但不完全等同晚报固定的今日09:00-15:00窗口。";
        }
        return "当前为最近" + lookbackDays + "天滚动窗口，不等同晚报固定的今日09:00-15:00窗口。";
    }

    private String boardComparisonNote(int hours) {
        return "当前为最近" + hours + "小时滚动窗口；若对照晚报，晚报固定窗口为今日09:00-15:00。";
    }

    private String clusterComparisonNote() {
        return "这是当前查询窗口内按 clusterKey 聚合后的事件簇分，不是单条公告原始分，也不是整只股票的晚报聚合分。";
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

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String joinText(String... values) {
        return String.join(" ", values == null ? new String[0] : values);
    }

    private String normalizeText(String value) {
        return safeText(value)
                .replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String limitClause(int limit) {
        return "LIMIT " + limit;
    }

    private record ClusterView(AStockRss representative,
                               int rawSignalScore,
                               int clusterScore,
                               int noticeCount,
                               LocalDateTime latestPubDate,
                               String signalSide,
                               String titles,
                               String eventType) {
    }

    private record StockAggregateView(String stockCode,
                                      String stockName,
                                      List<ClusterView> clusters,
                                      int aggregateSignalScore,
                                      int highValueNoticeCount,
                                      int eventClusterCount,
                                      String dominantSignalSide,
                                      LocalDateTime latestPubDate,
                                      String analysisHint,
                                      String clusterHighlights,
                                      ClusterView topCluster) {
    }

    private record ResonanceView(String themeName,
                                 int fusionScore,
                                 int macroSignalScore,
                                 String macroTitle,
                                 String relationReason) {

        private static ResonanceView empty() {
            return new ResonanceView(null, 0, 0, null, null);
        }

        private boolean hasResonance() {
            return StringUtils.hasText(themeName);
        }

        private boolean betterThan(ResonanceView other) {
            if (other == null || !other.hasResonance()) {
                return hasResonance();
            }
            if (!hasResonance()) {
                return false;
            }
            if (fusionScore != other.fusionScore) {
                return fusionScore > other.fusionScore;
            }
            return macroSignalScore > other.macroSignalScore;
        }
    }
}
