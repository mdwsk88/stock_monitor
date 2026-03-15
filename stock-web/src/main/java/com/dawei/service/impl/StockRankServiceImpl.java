package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import com.dawei.entity.AStockRssAggregate;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.entity.USStockRssAggregate;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.USStockRssMapper;
import com.dawei.service.StockRankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @ClassName StockRankServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description 股票异动排名服务实现类
 **/
@Slf4j
@Service
public class StockRankServiceImpl implements StockRankService {

    private final USStockRssMapper usStockRssMapper;
    private final AStockRssMapper aStockRssMapper;
    private final StockFilterConfig filterConfig;
    private final AStockSignalService aStockSignalService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StockRankServiceImpl(USStockRssMapper usStockRssMapper,
                                AStockRssMapper aStockRssMapper,
                                StockFilterConfig filterConfig,
                                AStockSignalService aStockSignalService) {
        this.usStockRssMapper = usStockRssMapper;
        this.aStockRssMapper = aStockRssMapper;
        this.filterConfig = filterConfig;
        this.aStockSignalService = aStockSignalService;
    }

    @Override
    public List<USStockRss> getUSTopNStocks(int limit) {
        return getUSTopNStocksWithFrequency(limit).stream()
                .map(StockAlertDTO::getStock)
                .collect(Collectors.toList());
    }

    @Override
    public List<AStockRss> getATopNStocks(int limit) {
        return getATopNStocksWithFrequency(limit).stream()
                .map(StockAlertDTO::getStock)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDTO<USStockRss>> getUSTopNStocksWithFrequency(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return getUSTopNStocksWithFrequency(limit, now.minusHours(24), now);
    }

    @Override
    public List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return getATopNStocksWithFrequency(limit, now.minusHours(24), now);
    }

    @Override
    public List<StockAlertDTO<USStockRss>> getUSTopNStocksWithFrequency(int limit, LocalDateTime startTime, LocalDateTime endTime) {
        String startTimeStr = startTime.format(FORMATTER);
        String endTimeStr = endTime.format(FORMATTER);
        List<String> blacklistKeywords = getBlacklistKeywords();

        log.info("SQL聚合查询美股异动排名，时间范围: {} 至 {}，阈值: {}次，返回上限: {}",
                startTimeStr, endTimeStr, filterConfig.getFrequencyThreshold(), limit);

        List<USStockRssAggregate> aggregatedRows = usStockRssMapper.selectTopStocksByFrequency(
                startTimeStr,
                endTimeStr,
                filterConfig.getFrequencyThreshold(),
                limit,
                blacklistKeywords
        );

        if (aggregatedRows.isEmpty()) {
            log.warn("指定时间范围内无美股异动数据");
            return new ArrayList<>();
        }

        enrichUSRowsWithAggregatedTitles(aggregatedRows, startTimeStr, endTimeStr, blacklistKeywords);

        log.info("美股 SQL 聚合完成，候选标的: {} 只", aggregatedRows.size());
        return aggregatedRows.stream()
                .map(row -> new StockAlertDTO<USStockRss>(row, row.getFrequency()))
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit, LocalDateTime startTime, LocalDateTime endTime) {
        String startTimeStr = startTime.format(FORMATTER);
        String endTimeStr = endTime.format(FORMATTER);
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date", startTimeStr)
                .le("pub_date", endTimeStr)
                .orderByDesc("pub_date")
                .orderByDesc("signal_score");

        log.info("Java聚合查询A股事件排名，时间范围: {} 至 {}，最低评分: {}，返回上限: {}",
                startTimeStr, endTimeStr, filterConfig.getARankingSignalThreshold(), limit);

        List<AStockRss> records = aStockRssMapper.selectList(queryWrapper);
        if (records == null || records.isEmpty()) {
            log.warn("指定时间范围内无A股公告数据");
            return new ArrayList<>();
        }

        List<AStockRss> normalizedRecords = records.stream()
                .map(this::normalizeARecord)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (normalizedRecords.isEmpty()) {
            log.warn("A股公告全部被规则过滤，无有效事件");
            return new ArrayList<>();
        }

        List<StockAlertDTO<AStockRss>> rankedAlerts = normalizedRecords.stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode))
                .values()
                .stream()
                .map(stockRecords -> buildAStockAlert(stockRecords, endTime))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(StockAlertDTO<AStockRss>::getSignalScore).reversed()
                        .thenComparing(Comparator.comparingInt(StockAlertDTO<AStockRss>::getEventCount).reversed())
                        .thenComparing((StockAlertDTO<AStockRss> dto) -> dto.getStock().getPubDate(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());

        log.info("A股 Java 聚合完成，有效候选标的: {} 只", rankedAlerts.size());
        return rankedAlerts;
    }

    private void enrichUSRowsWithAggregatedTitles(List<USStockRssAggregate> aggregatedRows,
                                                  String startTime,
                                                  String endTime,
                                                  List<String> blacklistKeywords) {
        List<String> stockCodes = aggregatedRows.stream()
                .map(USStockRss::getStockCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (stockCodes.isEmpty()) {
            return;
        }

        Map<String, List<USStockRss>> groupedRecords = usStockRssMapper.selectFilteredByStockCodes(
                        stockCodes, startTime, endTime, blacklistKeywords)
                .stream()
                .collect(Collectors.groupingBy(USStockRss::getStockCode));

        aggregatedRows.forEach(row ->
                row.setTags(aggregateTitlesForAnalysis(
                        groupedRecords.getOrDefault(row.getStockCode(), Collections.emptyList()))));
    }

    private List<String> getBlacklistKeywords() {
        List<String> blacklistKeywords = filterConfig.getBlacklistKeywords();
        return blacklistKeywords == null ? Collections.emptyList() : blacklistKeywords;
    }

    /**
     * 聚合美股相关标题用于AI分析
     * 将所有标题汇总，帮助AI提炼核心催化剂
     */
    private String aggregateTitlesForAnalysis(List<USStockRss> records) {
        return records.stream()
                .map(r -> {
                    String title = r.getTitleZh() != null && !r.getTitleZh().isEmpty()
                            ? r.getTitleZh() : r.getTitle();
                    return title != null ? title : "";
                })
                .filter(t -> !t.isEmpty())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(" | "));
    }

    /**
     * 聚合A股相关标题用于AI分析
     */
    private String aggregateATitlesForAnalysis(List<AStockRss> records) {
        return records.stream()
                .map(r -> r.getTitle() != null ? r.getTitle() : "")
                .filter(t -> !t.isEmpty())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(" | "));
    }

    private AStockRss normalizeARecord(AStockRss record) {
        if (record == null || record.getStockCode() == null || record.getStockCode().isBlank()) {
            return null;
        }
        if (!aStockSignalService.isPreferredEquityCode(record.getStockCode())) {
            return null;
        }
        return aStockSignalService.enrichNotice(record) ? record : null;
    }

    private StockAlertDTO<AStockRss> buildAStockAlert(List<AStockRss> stockRecords, LocalDateTime endTime) {
        if (stockRecords == null || stockRecords.isEmpty()) {
            return null;
        }

        List<AStockEventCluster> clusters = stockRecords.stream()
                .collect(Collectors.groupingBy(record -> record.getClusterKey() != null
                        ? record.getClusterKey()
                        : aStockSignalService.buildClusterKey(record, record.getEventType() != null ? record.getEventType() : "常规事项")))
                .values()
                .stream()
                .map(this::summarizeCluster)
                .sorted(Comparator
                        .comparingInt(AStockEventCluster::clusterScore).reversed()
                        .thenComparing(AStockEventCluster::latestPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (clusters.isEmpty()) {
            return null;
        }

        int stockSignalScore = computeStockSignalScore(clusters, endTime);
        if (!aStockSignalService.meetsRankingThreshold(stockSignalScore)) {
            return null;
        }

        AStockRss representative = clusters.get(0).representative();
        AStockRssAggregate aggregate = new AStockRssAggregate();
        BeanUtils.copyProperties(representative, aggregate);
        aggregate.setFrequency(stockRecords.size());
        aggregate.setSignalScore(stockSignalScore);
        aggregate.setEventCount(clusters.size());
        aggregate.setRawNoticeCount(stockRecords.size());
        aggregate.setRelatedTitles(aggregateClusterTitles(clusters));
        aggregate.setAnalysisHint(buildAnalysisHint(clusters, stockSignalScore));
        aggregate.setClusterHighlights(buildClusterHighlights(clusters));
        aggregate.setEventType(clusters.stream()
                .map(AStockEventCluster::eventType)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(" | ")));
        aggregate.setSignalSide(resolveOverallSignalSide(clusters));

        return new StockAlertDTO<>(aggregate, stockRecords.size(), stockSignalScore, clusters.size(), aggregate.getSignalSide());
    }

    private AStockEventCluster summarizeCluster(List<AStockRss> notices) {
        List<AStockRss> sortedNotices = notices.stream()
                .sorted(Comparator
                        .comparing((AStockRss notice) -> notice.getSignalScore() != null ? notice.getSignalScore() : 0)
                        .reversed()
                        .thenComparing(AStockRss::getPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        AStockRss representative = sortedNotices.get(0);
        int maxScore = representative.getSignalScore() != null ? representative.getSignalScore() : 0;
        int supportBonus = Math.min(18, Math.max(0, sortedNotices.size() - 1) * 6);
        int clusterScore = Math.min(120, maxScore + supportBonus);
        LocalDateTime latestPubDate = sortedNotices.stream()
                .map(AStockRss::getPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new AStockEventCluster(
                representative,
                clusterScore,
                sortedNotices.size(),
                latestPubDate,
                representative.getEventType(),
                representative.getSignalSide(),
                aggregateATitlesForAnalysis(sortedNotices)
        );
    }

    private int computeStockSignalScore(List<AStockEventCluster> clusters, LocalDateTime endTime) {
        int total = 0;
        for (int i = 0; i < clusters.size(); i++) {
            AStockEventCluster cluster = clusters.get(i);
            double weight = i == 0 ? 1.0 : (i == 1 ? 0.45 : 0.25);
            total += (int) Math.round(cluster.clusterScore() * weight);
        }

        total += Math.min(12, Math.max(0, clusters.size() - 1) * 4);
        AStockEventCluster topCluster = clusters.get(0);
        if (topCluster.latestPubDate() != null && topCluster.latestPubDate().isAfter(endTime.minusHours(6))) {
            total += 6;
        }
        return Math.min(total, 180);
    }

    private String aggregateClusterTitles(List<AStockEventCluster> clusters) {
        return clusters.stream()
                .map(AStockEventCluster::titles)
                .filter(Objects::nonNull)
                .flatMap(titles -> Arrays.stream(titles.split("\\s*\\|\\s*")))
                .map(String::trim)
                .filter(title -> !title.isEmpty())
                .distinct()
                .limit(12)
                .collect(Collectors.joining(" | "));
    }

    private String resolveOverallSignalSide(List<AStockEventCluster> clusters) {
        long bullish = clusters.stream().filter(cluster -> "利多".equals(cluster.signalSide())).count();
        long bearish = clusters.stream().filter(cluster -> "利空".equals(cluster.signalSide())).count();
        if (bullish > bearish) {
            return "利多";
        }
        if (bearish > bullish) {
            return "利空";
        }
        return clusters.get(0).signalSide();
    }

    private String buildAnalysisHint(List<AStockEventCluster> clusters, int stockSignalScore) {
        AStockEventCluster topCluster = clusters.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("最高优先级事件为【").append(topCluster.eventType()).append("】，方向=").append(topCluster.signalSide());
        sb.append("，总评分=").append(stockSignalScore).append(" 分");
        sb.append("，主导事件簇评分=").append(topCluster.clusterScore()).append(" 分");
        sb.append("，事件簇数=").append(clusters.size());
        sb.append("，支撑公告数=").append(clusters.stream().mapToInt(AStockEventCluster::noticeCount).sum());
        if (topCluster.latestPubDate() != null) {
            sb.append("，最新事件时间=").append(topCluster.latestPubDate().format(FORMATTER));
        }
        sb.append("。优先围绕最高分事件解读，不要平均分配篇幅。");
        return sb.toString();
    }

    private String buildClusterHighlights(List<AStockEventCluster> clusters) {
        return clusters.stream()
                .limit(3)
                .map(cluster -> String.format("%s | 方向=%s | 簇评分=%d | 支撑公告=%d | 代表标题=%s",
                        cluster.eventType(),
                        cluster.signalSide(),
                        cluster.clusterScore(),
                        cluster.noticeCount(),
                        cluster.representative().getTitle()))
                .collect(Collectors.joining("\n"));
    }

    private record AStockEventCluster(AStockRss representative,
                                      int clusterScore,
                                      int noticeCount,
                                      LocalDateTime latestPubDate,
                                      String eventType,
                                      String signalSide,
                                      String titles) {
    }
}
