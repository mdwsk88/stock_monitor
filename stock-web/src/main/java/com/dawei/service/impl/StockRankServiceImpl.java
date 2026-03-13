package com.dawei.service.impl;

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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StockRankServiceImpl(USStockRssMapper usStockRssMapper,
                                AStockRssMapper aStockRssMapper,
                                StockFilterConfig filterConfig) {
        this.usStockRssMapper = usStockRssMapper;
        this.aStockRssMapper = aStockRssMapper;
        this.filterConfig = filterConfig;
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
        List<String> blacklistKeywords = getBlacklistKeywords();

        log.info("SQL聚合查询A股公告异动排名，时间范围: {} 至 {}，阈值: {}次，返回上限: {}",
                startTimeStr, endTimeStr, filterConfig.getFrequencyThreshold(), limit);

        List<AStockRssAggregate> aggregatedRows = aStockRssMapper.selectTopStocksByFrequency(
                startTimeStr,
                endTimeStr,
                filterConfig.getFrequencyThreshold(),
                limit,
                blacklistKeywords
        );

        if (aggregatedRows.isEmpty()) {
            log.warn("指定时间范围内无A股公告数据");
            return new ArrayList<>();
        }

        enrichARowsWithAggregatedTitles(aggregatedRows, startTimeStr, endTimeStr, blacklistKeywords);

        log.info("A股 SQL 聚合完成，候选标的: {} 只", aggregatedRows.size());
        return aggregatedRows.stream()
                .map(row -> new StockAlertDTO<AStockRss>(row, row.getFrequency()))
                .collect(Collectors.toList());
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

    private void enrichARowsWithAggregatedTitles(List<AStockRssAggregate> aggregatedRows,
                                                 String startTime,
                                                 String endTime,
                                                 List<String> blacklistKeywords) {
        List<String> stockCodes = aggregatedRows.stream()
                .map(AStockRss::getStockCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (stockCodes.isEmpty()) {
            return;
        }

        Map<String, List<AStockRss>> groupedRecords = aStockRssMapper.selectFilteredByStockCodes(
                        stockCodes, startTime, endTime, blacklistKeywords)
                .stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode));

        aggregatedRows.forEach(row ->
                row.setTag(aggregateATitlesForAnalysis(
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
}
