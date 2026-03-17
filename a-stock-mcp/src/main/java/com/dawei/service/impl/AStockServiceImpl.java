package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.service.AStockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName AStockServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description A股数据服务实现类
 **/
@Service
public class AStockServiceImpl implements AStockService {

    private static final int DEFAULT_MIN_SIGNAL_SCORE = 60;
    private static final int DEFAULT_RESULT_LIMIT = 8;
    private static final int DEFAULT_COUNTS_LIMIT = 10;
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AStockRssMapper aStockRssMapper;

    @Override
    public List<AStockRss> queryStock(String stockCode) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.ge("signal_score", DEFAULT_MIN_SIGNAL_SCORE);
        queryWrapper.ge("pub_date", format(LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS)));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_RESULT_LIMIT));
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<AStockRss> queryStockByName(String stockName) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("stock_name", stockName);
        queryWrapper.ge("signal_score", DEFAULT_MIN_SIGNAL_SCORE);
        queryWrapper.ge("pub_date", format(LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS)));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_RESULT_LIMIT));
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<AStockRss> queryStockBetweenDate(String stockCode, String startDate, String endDate) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.between("pub_date", startDate, endDate);
        queryWrapper.ge("signal_score", DEFAULT_MIN_SIGNAL_SCORE);
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_RESULT_LIMIT));
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate) {
        Map<String, Object> map = new HashMap<>();
        map.put("targetCounts", targetCounts);
        map.put("startDate", startDate);
        map.put("endDate", endDate);
        map.put("minSignalScore", DEFAULT_MIN_SIGNAL_SCORE);
        map.put("limit", DEFAULT_COUNTS_LIMIT);
        return aStockRssMapper.queryStockCountsBetweenDate(map);
    }

    @Override
    public List<AStockRss> queryStockByTitleKeywords(List<String> titleKeywords) {
        return aStockRssMapper.queryStockByTitleKeywords(
                titleKeywords,
                DEFAULT_MIN_SIGNAL_SCORE,
                DEFAULT_RESULT_LIMIT
        );
    }

    @Override
    public List<AStockRss> queryStockByNameKeywords(List<String> nameKeywords) {
        return aStockRssMapper.queryStockByNameKeywords(
                nameKeywords,
                DEFAULT_MIN_SIGNAL_SCORE,
                DEFAULT_RESULT_LIMIT
        );
    }

    private String format(LocalDateTime dateTime) {
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private String limitClause(int limit) {
        return "LIMIT " + limit;
    }

}
