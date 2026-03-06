package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StockRankServiceImpl(USStockRssMapper usStockRssMapper, AStockRssMapper aStockRssMapper) {
        this.usStockRssMapper = usStockRssMapper;
        this.aStockRssMapper = aStockRssMapper;
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

        log.info("查询美股过去24小时异动排名（含频次），时间范围: {} 至 {}", startTime, endTime);

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

        // 按股票代码分组统计
        Map<String, List<USStockRss>> groupedByStockCode = allRecords.stream()
                .collect(Collectors.groupingBy(USStockRss::getStockCode));

        // 统计每只股票的出现次数，并取最新记录
        List<StockAlertDTO<USStockRss>> stockAlertList = new ArrayList<>();
        for (Map.Entry<String, List<USStockRss>> entry : groupedByStockCode.entrySet()) {
            List<USStockRss> records = entry.getValue();
            // 按时间排序取最新的一条
            USStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(USStockRss::getPubDateGmt))
                    .orElse(records.get(0));
            stockAlertList.add(new StockAlertDTO<>(latestRecord, records.size()));
        }

        // 按异动次数降序排序，取前N条
        return stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<USStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDTO<AStockRss>> getATopNStocksWithFrequency(int limit) {
        // 计算24小时前的时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        String startTime = yesterday.format(FORMATTER);
        String endTime = now.format(FORMATTER);

        log.info("查询A股过去24小时公告异动排名（含频次），时间范围: {} 至 {}", startTime, endTime);

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
        List<StockAlertDTO<AStockRss>> stockAlertList = new ArrayList<>();
        for (Map.Entry<String, List<AStockRss>> entry : groupedByStockCode.entrySet()) {
            List<AStockRss> records = entry.getValue();
            // 按时间排序取最新的一条
            AStockRss latestRecord = records.stream()
                    .max(Comparator.comparing(AStockRss::getPubDate))
                    .orElse(records.get(0));
            stockAlertList.add(new StockAlertDTO<>(latestRecord, records.size()));
        }

        // 按异动次数降序排序，取前N条
        return stockAlertList.stream()
                .sorted(Comparator.comparingInt(StockAlertDTO<AStockRss>::getFrequency).reversed())
                .limit(limit)
                .collect(Collectors.toList());
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
}
