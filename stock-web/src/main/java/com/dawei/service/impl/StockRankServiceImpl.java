package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.USStockRssMapper;
import com.dawei.service.StockRankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
        // 计算24小时前的时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        String startTime = yesterday.format(FORMATTER);
        String endTime = now.format(FORMATTER);

        log.info("查询美股过去24小时异动排名，时间范围: {} 至 {}", startTime, endTime);

        // 查询过去24小时内的所有记录
        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date_gmt", startTime);
        queryWrapper.le("pub_date_gmt", endTime);
        queryWrapper.orderByDesc("pub_date_gmt");

        List<USStockRss> allRecords = usStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("过去24小时内无美股异动数据");
            return new ArrayList<>();
        }

        // 按股票代码分组统计，并取每只股票最新的一条记录
        Map<String, List<USStockRss>> groupedByStockCode = allRecords.stream()
                .collect(Collectors.groupingBy(USStockRss::getStockCode));

        // 统计每只股票的出现次数，并取最新记录
        List<StockCount<USStockRss>> stockCounts = new ArrayList<>();
        for (Map.Entry<String, List<USStockRss>> entry : groupedByStockCode.entrySet()) {
            List<USStockRss> records = entry.getValue();
            // 按时间排序取最新的一条
            USStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(USStockRss::getPubDateGmt))
                    .orElse(records.get(0));
            stockCounts.add(new StockCount<>(latestRecord, records.size()));
        }

        // 按异动次数降序排序，取前N条
        return stockCounts.stream()
                .sorted(Comparator.comparingInt(StockCount<USStockRss>::getCount).reversed())
                .limit(limit)
                .map(StockCount::getStock)
                .collect(Collectors.toList());
    }

    @Override
    public List<AStockRss> getATopNStocks(int limit) {
        // 计算24小时前的时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        String startTime = yesterday.format(FORMATTER);
        String endTime = now.format(FORMATTER);

        log.info("查询A股过去24小时公告异动排名，时间范围: {} 至 {}", startTime, endTime);

        // 查询过去24小时内的所有记录
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date", startTime);
        queryWrapper.le("pub_date", endTime);
        queryWrapper.orderByDesc("pub_date");

        List<AStockRss> allRecords = aStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("过去24小时内无A股公告数据");
            return new ArrayList<>();
        }

        // 按股票代码分组统计
        Map<String, List<AStockRss>> groupedByStockCode = allRecords.stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode));

        // 统计每只股票的出现次数，并取最新记录
        List<StockCount<AStockRss>> stockCounts = new ArrayList<>();
        for (Map.Entry<String, List<AStockRss>> entry : groupedByStockCode.entrySet()) {
            List<AStockRss> records = entry.getValue();
            // 按时间排序取最新的一条
            AStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(AStockRss::getPubDate))
                    .orElse(records.get(0));
            stockCounts.add(new StockCount<>(latestRecord, records.size()));
        }

        // 按公告次数降序排序，取前N条
        return stockCounts.stream()
                .sorted(Comparator.comparingInt(StockCount<AStockRss>::getCount).reversed())
                .limit(limit)
                .map(StockCount::getStock)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDTO<USStockRss>> getUSTopNStocksWithFrequency(int limit) {
        // 计算24小时前的时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        String startTime = yesterday.format(FORMATTER);
        String endTime = now.format(FORMATTER);

        log.info("查询美股过去24小时异动排名（含频次），时间范围: {} 至 {}，阈值: {}次", 
                startTime, endTime, filterConfig.getFrequencyThreshold());

        // 查询过去24小时内的所有记录
        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date_gmt", startTime);
        queryWrapper.le("pub_date_gmt", endTime);
        queryWrapper.orderByDesc("pub_date_gmt");

        List<USStockRss> allRecords = usStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("过去24小时内无美股异动数据");
            return new ArrayList<>();
        }

        // 第一步：过滤掉包含黑名单关键词的记录
        List<USStockRss> filteredRecords = allRecords.stream()
                .filter(record -> {
                    // 检查中文标题和英文标题是否包含黑名单词
                    String titleZh = record.getTitleZh() != null ? record.getTitleZh() : "";
                    String titleEn = record.getTitle() != null ? record.getTitle() : "";
                    
                    boolean shouldFilter = filterConfig.containsBlacklistKeyword(titleZh) 
                            || filterConfig.containsBlacklistKeyword(titleEn);
                    
                    if (shouldFilter) {
                        log.debug("美股记录被过滤 - {}: 标题包含黑名单词", record.getStockCode());
                    }
                    return !shouldFilter;
                })
                .collect(Collectors.toList());

        log.info("美股原始记录: {} 条，黑名单过滤后: {} 条，过滤掉 {} 条噪音数据", 
                allRecords.size(), filteredRecords.size(), allRecords.size() - filteredRecords.size());

        // 第二步：按股票代码分组统计
        Map<String, List<USStockRss>> groupedByStockCode = filteredRecords.stream()
                .collect(Collectors.groupingBy(USStockRss::getStockCode));

        // 第三步：统计每只股票的出现次数，并取最新记录
        List<StockAlertDTO<USStockRss>> stockAlertList = new ArrayList<>();
        int belowThresholdCount = 0;
        
        for (Map.Entry<String, List<USStockRss>> entry : groupedByStockCode.entrySet()) {
            List<USStockRss> records = entry.getValue();
            int frequency = records.size();
            
            // 频次阈值过滤 - 宁缺毋滥
            if (!filterConfig.meetsFrequencyThreshold(frequency)) {
                belowThresholdCount++;
                log.debug("美股标的被过滤 - {}: 频次 {} 未达到阈值 {}", 
                        entry.getKey(), frequency, filterConfig.getFrequencyThreshold());
                continue;
            }
            
            // 按时间排序取最新的一条
            USStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(USStockRss::getPubDateGmt))
                    .orElse(records.get(0));
            
            // 附加所有相关标题供AI分析（用于提炼催化剂）
            latestRecord.setTags(aggregateTitlesForAnalysis(records));
            
            stockAlertList.add(new StockAlertDTO<>(latestRecord, frequency));
        }

        log.info("美股分组后标的: {} 只，低于阈值被过滤: {} 只，最终候选: {} 只", 
                groupedByStockCode.size(), belowThresholdCount, stockAlertList.size());

        // 按异动次数降序排序，取前N条
        List<StockAlertDTO<USStockRss>> result = stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<USStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("美股最终返回 TOP{} 异动标的", result.size());
        return result;
    }

    @Override
    public List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit) {
        // 计算24小时前的时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        String startTime = yesterday.format(FORMATTER);
        String endTime = now.format(FORMATTER);

        log.info("查询A股过去24小时公告异动排名（含频次），时间范围: {} 至 {}，阈值: {}次", 
                startTime, endTime, filterConfig.getFrequencyThreshold());

        // 查询过去24小时内的所有记录
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date", startTime);
        queryWrapper.le("pub_date", endTime);
        queryWrapper.orderByDesc("pub_date");

        List<AStockRss> allRecords = aStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("过去24小时内无A股公告数据");
            return new ArrayList<>();
        }

        // 第一步：过滤掉包含黑名单关键词的记录
        List<AStockRss> filteredRecords = allRecords.stream()
                .filter(record -> {
                    String title = record.getTitle() != null ? record.getTitle() : "";
                    boolean shouldFilter = filterConfig.containsBlacklistKeyword(title);
                    
                    if (shouldFilter) {
                        log.debug("A股公告被过滤 - {}: 标题包含黑名单词: {}", 
                                record.getStockCode(), title.substring(0, Math.min(30, title.length())));
                    }
                    return !shouldFilter;
                })
                .collect(Collectors.toList());

        log.info("A股原始公告: {} 条，黑名单过滤后: {} 条，过滤掉 {} 条噪音公告", 
                allRecords.size(), filteredRecords.size(), allRecords.size() - filteredRecords.size());

        // 第二步：按股票代码分组统计
        Map<String, List<AStockRss>> groupedByStockCode = filteredRecords.stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode));

        // 第三步：统计每只股票的出现次数，并取最新记录
        List<StockAlertDTO<AStockRss>> stockAlertList = new ArrayList<>();
        int belowThresholdCount = 0;
        
        for (Map.Entry<String, List<AStockRss>> entry : groupedByStockCode.entrySet()) {
            List<AStockRss> records = entry.getValue();
            int frequency = records.size();
            
            // 频次阈值过滤 - 宁缺毋滥
            if (!filterConfig.meetsFrequencyThreshold(frequency)) {
                belowThresholdCount++;
                log.debug("A股标的被过滤 - {}: 频次 {} 未达到阈值 {}", 
                        entry.getKey(), frequency, filterConfig.getFrequencyThreshold());
                continue;
            }
            
            // 按时间排序取最新的一条
            AStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(AStockRss::getPubDate))
                    .orElse(records.get(0));
            
            // 聚合所有标题供AI分析（用于提炼催化剂）
            latestRecord.setTag(aggregateATitlesForAnalysis(records));
            
            stockAlertList.add(new StockAlertDTO<>(latestRecord, frequency));
        }

        log.info("A股分组后标的: {} 只，低于阈值被过滤: {} 只，最终候选: {} 只", 
                groupedByStockCode.size(), belowThresholdCount, stockAlertList.size());

        // 按异动次数降序排序，取前N条
        List<StockAlertDTO<AStockRss>> result = stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<AStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("A股最终返回 TOP{} 异动标的", result.size());
        return result;
    }

    @Override
    public List<StockAlertDTO<USStockRss>> getUSTopNStocksWithFrequency(int limit, LocalDateTime startTime, LocalDateTime endTime) {
        String startTimeStr = startTime.format(FORMATTER);
        String endTimeStr = endTime.format(FORMATTER);

        log.info("查询美股指定时间范围异动排名（含频次），时间范围: {} 至 {}，阈值: {}次", 
                startTimeStr, endTimeStr, filterConfig.getFrequencyThreshold());

        // 查询指定时间范围内的所有记录
        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date_gmt", startTimeStr);
        queryWrapper.le("pub_date_gmt", endTimeStr);
        queryWrapper.orderByDesc("pub_date_gmt");

        List<USStockRss> allRecords = usStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("指定时间范围内无美股异动数据");
            return new ArrayList<>();
        }

        // 第一步：过滤掉包含黑名单关键词的记录
        List<USStockRss> filteredRecords = allRecords.stream()
                .filter(record -> {
                    String titleZh = record.getTitleZh() != null ? record.getTitleZh() : "";
                    String titleEn = record.getTitle() != null ? record.getTitle() : "";
                    
                    boolean shouldFilter = filterConfig.containsBlacklistKeyword(titleZh) 
                            || filterConfig.containsBlacklistKeyword(titleEn);
                    
                    if (shouldFilter) {
                        log.debug("美股记录被过滤 - {}: 标题包含黑名单词", record.getStockCode());
                    }
                    return !shouldFilter;
                })
                .collect(Collectors.toList());

        log.info("美股原始记录: {} 条，黑名单过滤后: {} 条，过滤掉 {} 条噪音数据", 
                allRecords.size(), filteredRecords.size(), allRecords.size() - filteredRecords.size());

        // 第二步：按股票代码分组统计
        Map<String, List<USStockRss>> groupedByStockCode = filteredRecords.stream()
                .collect(Collectors.groupingBy(USStockRss::getStockCode));

        // 第三步：统计每只股票的出现次数，并取最新记录
        List<StockAlertDTO<USStockRss>> stockAlertList = new ArrayList<>();
        int belowThresholdCount = 0;
        
        for (Map.Entry<String, List<USStockRss>> entry : groupedByStockCode.entrySet()) {
            List<USStockRss> records = entry.getValue();
            int frequency = records.size();
            
            // 频次阈值过滤 - 宁缺毋滥
            if (!filterConfig.meetsFrequencyThreshold(frequency)) {
                belowThresholdCount++;
                log.debug("美股标的被过滤 - {}: 频次 {} 未达到阈值 {}", 
                        entry.getKey(), frequency, filterConfig.getFrequencyThreshold());
                continue;
            }
            
            // 按时间排序取最新的一条
            USStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(USStockRss::getPubDateGmt))
                    .orElse(records.get(0));
            
            // 附加所有相关标题供AI分析（用于提炼催化剂）
            latestRecord.setTags(aggregateTitlesForAnalysis(records));
            
            stockAlertList.add(new StockAlertDTO<>(latestRecord, frequency));
        }

        log.info("美股分组后标的: {} 只，低于阈值被过滤: {} 只，最终候选: {} 只", 
                groupedByStockCode.size(), belowThresholdCount, stockAlertList.size());

        // 按异动次数降序排序，取前N条
        List<StockAlertDTO<USStockRss>> result = stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<USStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("美股最终返回 TOP{} 异动标的", result.size());
        return result;
    }

    @Override
    public List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit, LocalDateTime startTime, LocalDateTime endTime) {
        String startTimeStr = startTime.format(FORMATTER);
        String endTimeStr = endTime.format(FORMATTER);

        log.info("查询A股指定时间范围公告异动排名（含频次），时间范围: {} 至 {}，阈值: {}次", 
                startTimeStr, endTimeStr, filterConfig.getFrequencyThreshold());

        // 查询指定时间范围内的所有记录
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date", startTimeStr);
        queryWrapper.le("pub_date", endTimeStr);
        queryWrapper.orderByDesc("pub_date");

        List<AStockRss> allRecords = aStockRssMapper.selectList(queryWrapper);

        if (allRecords.isEmpty()) {
            log.warn("指定时间范围内无A股公告数据");
            return new ArrayList<>();
        }

        // 第一步：过滤掉包含黑名单关键词的记录
        List<AStockRss> filteredRecords = allRecords.stream()
                .filter(record -> {
                    String title = record.getTitle() != null ? record.getTitle() : "";
                    boolean shouldFilter = filterConfig.containsBlacklistKeyword(title);
                    
                    if (shouldFilter) {
                        log.debug("A股公告被过滤 - {}: 标题包含黑名单词: {}", 
                                record.getStockCode(), title.substring(0, Math.min(30, title.length())));
                    }
                    return !shouldFilter;
                })
                .collect(Collectors.toList());

        log.info("A股原始公告: {} 条，黑名单过滤后: {} 条，过滤掉 {} 条噪音公告", 
                allRecords.size(), filteredRecords.size(), allRecords.size() - filteredRecords.size());

        // 第二步：按股票代码分组统计
        Map<String, List<AStockRss>> groupedByStockCode = filteredRecords.stream()
                .collect(Collectors.groupingBy(AStockRss::getStockCode));

        // 第三步：统计每只股票的出现次数，并取最新记录
        List<StockAlertDTO<AStockRss>> stockAlertList = new ArrayList<>();
        int belowThresholdCount = 0;
        
        for (Map.Entry<String, List<AStockRss>> entry : groupedByStockCode.entrySet()) {
            List<AStockRss> records = entry.getValue();
            int frequency = records.size();
            
            // 频次阈值过滤 - 宁缺毋滥
            if (!filterConfig.meetsFrequencyThreshold(frequency)) {
                belowThresholdCount++;
                log.debug("A股标的被过滤 - {}: 频次 {} 未达到阈值 {}", 
                        entry.getKey(), frequency, filterConfig.getFrequencyThreshold());
                continue;
            }
            
            // 按时间排序取最新的一条
            AStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(AStockRss::getPubDate))
                    .orElse(records.get(0));
            
            // 聚合所有标题供AI分析（用于提炼催化剂）
            latestRecord.setTag(aggregateATitlesForAnalysis(records));
            
            stockAlertList.add(new StockAlertDTO<>(latestRecord, frequency));
        }

        log.info("A股分组后标的: {} 只，低于阈值被过滤: {} 只，最终候选: {} 只", 
                groupedByStockCode.size(), belowThresholdCount, stockAlertList.size());

        // 按异动次数降序排序，取前N条
        List<StockAlertDTO<AStockRss>> result = stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<AStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("A股最终返回 TOP{} 异动标的", result.size());
        return result;
    }

    /**
     * 内部类：用于统计股票出现次数
     */
    private static class StockCount<T> {
        private final T stock;
        private final int count;

        public StockCount(T stock, int count) {
            this.stock = stock;
            this.count = count;
        }

        public T getStock() {
            return stock;
        }

        public int getCount() {
            return count;
        }
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
                .limit(10) // 最多取10条不同标题，避免过长
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
                .limit(10) // 最多取10条不同标题，避免过长
                .collect(Collectors.joining(" | "));
    }
}
