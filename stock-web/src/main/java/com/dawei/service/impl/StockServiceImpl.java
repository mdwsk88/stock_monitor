package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.USStockRss;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.USStockRssMapper;
import com.dawei.service.StockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @ClassName StockServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description StockServiceImpl
 **/
@Service
public class StockServiceImpl implements StockService {

    @Resource
    private USStockRssMapper usStockRssMapper;

    @Resource
    private AStockRssMapper aStockRssMapper;

    // ============== 美股相关方法 ==============

    @Override
    public boolean saveStockNewsIfAbsent(USStockRss stockNews) {
        ensureId(stockNews);
        return usStockRssMapper.insertIgnore(stockNews) > 0;
    }

    @Override
    public Long getStockUnusualCounts(USStockRss stockNews, LocalDateTime startDate, LocalDateTime endDate) {

        String stockCode = stockNews.getStockCode();

        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);

//        数据库中的日期时间格式： yyyy-MM-dd HH:mm:ss
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startDateStr = formatter.format(startDate);
        String endDateStr = formatter.format(endDate);

        queryWrapper.ge("pub_date_gmt", startDateStr);
        queryWrapper.le("pub_date_gmt", endDateStr);

        return usStockRssMapper.selectCount(queryWrapper);
    }

    // ============== A股相关方法 ==============

    @Override
    public boolean saveAStockNewsIfAbsent(AStockRss aStockNews) {
        ensureId(aStockNews);
        return aStockRssMapper.insertIgnore(aStockNews) > 0;
    }

    @Override
    public Long getAStockNoticeCounts(String stockCode, LocalDateTime startDate, LocalDateTime endDate) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);

//        数据库中的日期时间格式： yyyy-MM-dd HH:mm:ss
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startDateStr = formatter.format(startDate);
        String endDateStr = formatter.format(endDate);

        queryWrapper.ge("pub_date", startDateStr);
        queryWrapper.le("pub_date", endDateStr);

        return aStockRssMapper.selectCount(queryWrapper);
    }

    private void ensureId(USStockRss stockNews) {
        if (stockNews.getId() == null || stockNews.getId().isBlank()) {
            stockNews.setId(UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private void ensureId(AStockRss aStockNews) {
        if (aStockNews.getId() == null || aStockNews.getId().isBlank()) {
            aStockNews.setId(UUID.randomUUID().toString().replace("-", ""));
        }
    }
}
